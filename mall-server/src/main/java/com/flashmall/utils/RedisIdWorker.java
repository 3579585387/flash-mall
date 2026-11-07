package com.flashmall.utils;

import com.flashmall.common.constant.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 全局唯一 ID 生成器
 * <p>
 * 结构（64 位 Long）：
 * <pre>
 *   0 | 31 位时间戳(秒，相对基准时间) | 32 位序列号(当天自增)
 * </pre>
 * 相比数据库自增：不暴露业务量、趋势递增（B+ 树友好）、可分库分表使用。
 * 相比 UUID：有序、占空间小（8 字节）。
 */
@Component
public class RedisIdWorker {

    /** 基准时间戳：2024-01-01 00:00:00 UTC，越晚的订单号越大 */
    private static final long BEGIN_TIMESTAMP = 1704067200L;

    /** 序列号占用位数 */
    private static final int COUNT_BITS = 32;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd");

    private final StringRedisTemplate redis;

    public RedisIdWorker(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 注意：所有写入同一张订单表的业务必须使用同一个 keyPrefix。
     * 不同前缀使用独立的序列计数器，在同一秒内可能生成完全相同的 64 位 ID
     * （普通订单和秒杀订单都写 tb_orders，因此统一使用 "order"）。
     *
     * @param keyPrefix 业务前缀，订单相关统一使用 order
     */
    public long nextId(String keyPrefix) {
        long timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - BEGIN_TIMESTAMP;

        // 按天拆分 key：既能保证每天的计数从 1 开始，也避免单个 key 无限增长
        String date = LocalDate.now().format(DATE_FORMATTER);
        String key = RedisConstants.ID_WORKER_KEY + keyPrefix + ":" + date;

        Long seq = redis.opsForValue().increment(key);
        if (seq != null && seq == 1L) {
            redis.expire(key, Duration.ofDays(2));
        }
        long sequence = seq == null ? 0L : seq;
        return (timestamp << COUNT_BITS) | sequence;
    }
}
