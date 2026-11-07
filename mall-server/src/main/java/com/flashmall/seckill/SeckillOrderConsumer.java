package com.flashmall.seckill;

import com.flashmall.common.constant.RedisConstants;
import com.flashmall.common.exception.BizException;
import com.flashmall.utils.RedisVersionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 秒杀下单消费者：从 Redis Stream 拉取消息，异步创建订单
 * <p>
 * 可靠性设计与原版"单线程 + 只重试"的做法相比，补了四件事：
 * 1. 多消费线程，避免单线程成为吞吐瓶颈；
 * 2. 幂等（tb_mq_message 唯一键），重复投递不会重复下单；
 * 3. 业务异常（库存不足、重复下单）判定为不可重试，直接回补库存 + 进死信；
 * 4. 系统异常（数据库抖动等）先重试，超过 maxRetry 次才放弃，
 *    PENDING 列表由独立线程定期扫描兜底，保证"进程崩溃导致没 ACK"的消息也不会丢。
 */
@Slf4j
@Component
public class SeckillOrderConsumer {

    private final StringRedisTemplate redis;
    private final SeckillOrderCreator orderCreator;

    @Value("${flash-mall.seckill.consumer-threads:2}")
    private int consumerThreads;

    @Value("${flash-mall.seckill.max-retry:3}")
    private int maxRetry;

    private ExecutorService executor;
    private volatile boolean running = true;

    /** 异常日志降噪：前几次打印完整堆栈，之后只打印摘要 */
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicLong lastErrorLogAt = new AtomicLong(0);

    public SeckillOrderConsumer(StringRedisTemplate redis, SeckillOrderCreator orderCreator) {
        this.redis = redis;
        this.orderCreator = orderCreator;
    }

    @PostConstruct
    public void start() {
        // Redis Stream 需要 5.0+。版本不够时不要启动消费线程，
        // 否则会每秒刷一次 "ERR unknown command 'XREADGROUP'" 把日志冲爆。
        if (!RedisVersionUtil.supportsStream(redis)) {
            log.error("Redis 版本 {} 不支持 Stream（需要 5.0+），秒杀消费者不启动。"
                            + " 抢购接口也会不可用，请升级 Redis（工作区 启动Redis.bat 可一键换新版本）。",
                    RedisVersionUtil.getVersion(redis));
            return;
        }

        createConsumerGroup();

        executor = Executors.newFixedThreadPool(consumerThreads + 1);
        for (int i = 0; i < consumerThreads; i++) {
            final String consumerName = "consumer-" + i;
            executor.submit(() -> consumeLoop(consumerName));
        }
        // 独立的 PENDING 扫描线程
        executor.submit(this::pendingLoop);
        log.info("秒杀消费者启动完成：消费线程 {}，最大重试 {} 次", consumerThreads, maxRetry);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (executor != null) {
            executor.shutdownNow();
        }
        log.info("秒杀消费者已停止");
    }

    /**
     * 创建消费组：用 Lua 的 pcall 包住 XGROUP CREATE，
     * 消费组已存在（BUSYGROUP）时不会抛异常，MKSTREAM 保证 stream 不存在也能建组。
     */
    private void createConsumerGroup() {
        try {
            redis.execute(SeckillScriptHolder.CREATE_GROUP_SCRIPT,
                    Collections.singletonList(RedisConstants.STREAM_SECKILL),
                    RedisConstants.STREAM_SECKILL_GROUP);
            log.info("秒杀消费组已就绪: {}", RedisConstants.STREAM_SECKILL_GROUP);
        } catch (Exception e) {
            log.warn("创建秒杀消费组失败，请确认 Redis 已启动: {}", e.getMessage());
        }
    }

    /* ==================== 主消费循环 ==================== */

    private void consumeLoop(String consumerName) {
        while (running) {
            try {
                List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                        Consumer.from(RedisConstants.STREAM_SECKILL_GROUP, consumerName),
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create(RedisConstants.STREAM_SECKILL, ReadOffset.lastConsumed()));

                if (records == null || records.isEmpty()) {
                    continue;
                }
                for (MapRecord<String, Object, Object> record : records) {
                    handle(record, consumerName);
                }
            } catch (Exception e) {
                if (!running) {
                    return;
                }
                logConsumeError(consumerName, e);
                // 失败后稍作退避，避免空转把 CPU 和日志打满
                sleepQuietly(1000);
            }
        }
    }

    /**
     * 消费异常日志降噪
     * <p>
     * 连接异常、命令不支持这类问题会持续发生，如果每次都打完整堆栈，
     * 日志会被瞬间冲爆（原来 2 个消费线程每秒能刷出上百行）。
     * 策略：前 3 次带堆栈，之后每 30 秒只打一条摘要。
     */
    private void logConsumeError(String consumerName, Exception e) {
        int count = errorCount.incrementAndGet();
        if (count <= 3) {
            log.error("消费秒杀消息异常[{}]（第 {} 次，之后只打印摘要）: {}",
                    consumerName, count, e.getMessage(), e);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastErrorLogAt.get() > 30_000) {
            lastErrorLogAt.set(now);
            log.warn("消费秒杀消息持续异常[{}]: {}（累计已忽略 {} 次）",
                    consumerName, e.getMessage(), count);
        }
    }

    /* ==================== PENDING 兜底 ==================== */

    private void pendingLoop() {
        while (running) {
            sleepQuietly(30_000);
            if (!running) {
                return;
            }
            for (int i = 0; i < consumerThreads; i++) {
                retryPending("consumer-" + i);
            }
        }
    }

    /**
     * 读取某个消费者的 PENDING 消息（已投递但未 ACK 的消息，通常是处理中进程崩溃了）
     */
    private void retryPending(String consumerName) {
        try {
            List<MapRecord<String, Object, Object>> pending = redis.opsForStream().read(
                    Consumer.from(RedisConstants.STREAM_SECKILL_GROUP, consumerName),
                    StreamReadOptions.empty().count(10),
                    StreamOffset.create(RedisConstants.STREAM_SECKILL, ReadOffset.from("0")));
            if (pending == null || pending.isEmpty()) {
                return;
            }
            log.warn("[{}] 发现 {} 条 PENDING 消息，开始重试", consumerName, pending.size());
            for (MapRecord<String, Object, Object> record : pending) {
                handle(record, consumerName);
            }
        } catch (Exception e) {
            log.error("处理 PENDING 消息失败[{}]: {}", consumerName, e.getMessage());
        }
    }

    /* ==================== 单条消息处理 ==================== */

    private void handle(MapRecord<String, Object, Object> record, String consumerName) {
        String recordId = record.getId().getValue();
        Map<Object, Object> value = record.getValue();

        String orderNo = asString(value.get("orderNo"));
        Long userId = asLong(value.get("userId"));
        Long activityId = asLong(value.get("activityId"));
        Long skuId = asLong(value.get("skuId"));

        if (orderNo == null || userId == null || activityId == null || skuId == null) {
            log.error("消息格式非法，丢弃: id={}, value={}", recordId, value);
            ack(recordId);
            return;
        }

        try {
            orderCreator.create(orderNo, userId, activityId, skuId);
            markResult(orderNo, RedisConstants.SECKILL_RESULT_SUCCESS);
            log.info("[{}] 秒杀订单创建成功 orderNo={}", consumerName, orderNo);
            ack(recordId);
        } catch (DuplicateKeyException e) {
            // 消息重复投递，或该用户已经下过单：都视为"已处理"
            log.info("[{}] 重复消息/重复下单，跳过 orderNo={}", consumerName, orderNo);
            markResult(orderNo, RedisConstants.SECKILL_RESULT_SUCCESS);
            ack(recordId);
        } catch (BizException e) {
            // 业务异常属于"重试也没用"，直接失败并回补库存，避免库存被永久占用。
            // 只有回补成功才 ACK；回补失败则留在 PENDING，等 pendingLoop 重试。
            log.warn("[{}] 秒杀订单业务失败 orderNo={}, reason={}", consumerName, orderNo, e.getMessage());
            if (failAndRollback(activityId, skuId, userId, orderNo, e.getMessage())) {
                ack(recordId);
            }
        } catch (Exception e) {
            // 系统异常（数据库抖动/网络）：有限次重试
            Long retry = redis.opsForValue().increment(RedisConstants.SECKILL_RETRY_KEY + orderNo);
            long retryCount = retry == null ? 1L : retry;
            if (retryCount <= maxRetry) {
                log.error("[{}] 第 {} 次处理失败，留在 PENDING 稍后重试 orderNo={}",
                        consumerName, retryCount, orderNo, e);
                // 不 ACK：消息留在 PENDING 列表，由 pendingLoop 重试
            } else {
                log.error("[{}] 重试 {} 次仍失败，放弃并回补库存 orderNo={}", consumerName, retryCount, orderNo, e);
                // 回补成功才 ACK；失败则保留重试计数，下次继续尝试回补，不能静默丢库存
                if (failAndRollback(activityId, skuId, userId, orderNo, "系统繁忙")) {
                    redis.delete(RedisConstants.SECKILL_RETRY_KEY + orderNo);
                    ack(recordId);
                }
            }
        }
    }

    /**
     * 失败处理：标记结果 + 回补 Redis 库存 + 进死信队列
     * <p>
     * 回补必须做，否则"库存被扣了但订单没生成"，这部分库存就永远卖不出去了。
     *
     * @return true 表示 Redis 库存已经回补成功（或之前已经回补过），调用方可以 ACK；
     *         false 表示回补失败，调用方必须保留消息在 PENDING 中稍后重试
     */
    private boolean failAndRollback(Long activityId, Long skuId, Long userId, String orderNo, String reason) {
        markResult(orderNo, RedisConstants.SECKILL_RESULT_FAIL);

        String rollbackGuard = RedisConstants.SECKILL_RETRY_KEY + "rollback:" + orderNo;
        boolean rolledBack = false;
        try {
            Boolean first = redis.opsForValue().setIfAbsent(rollbackGuard, "1", Duration.ofHours(1));
            if (Boolean.TRUE.equals(first)) {
                try {
                    redis.execute(SeckillScriptHolder.ROLLBACK_SCRIPT,
                            List.of(RedisConstants.seckillStockKey(activityId, skuId),
                                    RedisConstants.seckillUserKey(activityId, skuId),
                                    RedisConstants.seckillSoldoutKey(activityId, skuId)),
                            String.valueOf(userId));
                    log.info("已回补 Redis 库存 orderNo={}", orderNo);
                    rolledBack = true;
                } catch (Exception e) {
                    // 释放 guard，避免后续重试因为 guard 已存在而永远跳过回补
                    redis.delete(rollbackGuard);
                    log.error("回补 Redis 库存失败，稍后重试 orderNo={}", orderNo, e);
                }
            } else {
                // guard 已存在说明之前的尝试已经回补成功
                rolledBack = true;
            }
        } catch (Exception e) {
            log.error("回补 Redis 库存异常，稍后重试 orderNo={}", orderNo, e);
        }

        // 死信队列：留证据，方便排查和人工补偿
        Map<String, String> dlq = new HashMap<>(4);
        dlq.put("orderNo", orderNo);
        dlq.put("activityId", String.valueOf(activityId));
        dlq.put("skuId", String.valueOf(skuId));
        dlq.put("reason", reason == null ? "unknown" : reason);
        try {
            redis.opsForStream().add(RedisConstants.STREAM_SECKILL_DLQ, dlq);
            // 死信队列也做容量上限，避免长期运行无限增长
            redis.opsForStream().trim(RedisConstants.STREAM_SECKILL_DLQ, 10_000, true);
        } catch (Exception e) {
            log.error("写入死信队列失败 orderNo={}", orderNo, e);
        }
        return rolledBack;
    }

    /* ==================== 工具方法 ==================== */

    private void markResult(String orderNo, String status) {
        try {
            redis.opsForValue().set(RedisConstants.SECKILL_RESULT_KEY + orderNo, status,
                    Duration.ofMinutes(RedisConstants.SECKILL_RESULT_TTL_MINUTES));
        } catch (Exception e) {
            log.warn("写入秒杀结果失败 orderNo={}", orderNo, e);
        }
    }

    private void ack(String recordId) {
        try {
            redis.execute(SeckillScriptHolder.ACK_SCRIPT,
                    Collections.singletonList(RedisConstants.STREAM_SECKILL),
                    RedisConstants.STREAM_SECKILL_GROUP, recordId);
        } catch (Exception e) {
            log.error("消息 ACK 失败 id={}", recordId, e);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
