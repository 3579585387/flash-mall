package com.flashmall.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashmall.common.constant.RedisConstants;
import com.flashmall.common.exception.BizException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 缓存模板：把「穿透 / 击穿 / 雪崩」三种问题的解法封装成可复用方法
 * <p>
 * 三级缓存结构：Caffeine 本地缓存 → Redis → MySQL
 * <p>
 * 用法：
 * <pre>
 *   // 防穿透（带空值缓存）
 *   GoodsDetailVO vo = cacheTemplate.queryWithPassThrough(key, GoodsDetailVO.class, this::load, 30, TimeUnit.MINUTES);
 *   // 防击穿（逻辑过期 + 异步重建，不阻塞用户）
 *   GoodsDetailVO vo = cacheTemplate.queryWithLogicalExpire(key, GoodsDetailVO.class, this::load, 30, TimeUnit.MINUTES);
 * </pre>
 */
@Slf4j
@Component
public class CacheTemplate {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /** 一级缓存：本地 Caffeine，扛热点 key，10 秒过期保证最终能看到更新 */
    private final Cache<String, String> localCache;

    /** 缓存重建线程池：避免大量线程同时抢锁后一起查库 */
    private static final ExecutorService REBUILD_EXECUTOR = Executors.newFixedThreadPool(4);

    public CacheTemplate(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.localCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(10))
                .build();
    }

    /* ==================== 写 ==================== */

    /** 写入带真实 TTL 的缓存 */
    public void set(String key, Object value, long ttl, TimeUnit unit) {
        if (value == null) {
            return;
        }
        String json = toJson(value);
        redis.opsForValue().set(key, json, ttl, unit);
        localCache.put(key, json);
    }

    /** 写入逻辑过期缓存（不设置 Redis TTL） */
    public void setWithLogicalExpire(String key, Object value, long ttl, TimeUnit unit) {
        if (value == null) {
            return;
        }
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(ttl)));
        String json = toJson(redisData);
        redis.opsForValue().set(key, json);
        localCache.put(key, json);
    }

    /** 删除缓存（更新数据后必须调用，否则会读到旧值） */
    public void invalidate(String key) {
        redis.delete(key);
        localCache.invalidate(key);
    }

    /**
     * 写入空值标记，防止同一个不存在的 id 反复打数据库
     * <p>
     * 逻辑过期模板对空串按"未命中"处理，所以商品详情这类使用逻辑过期的场景
     * 需要在未命中后额外调用 {@link #isNullMarker(String)} 判断，避免穿透。
     */
    public void setNull(String key) {
        redis.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL_MINUTES, TimeUnit.MINUTES);
        localCache.put(key, "");
    }

    /** 是否命中空值标记 */
    public boolean isNullMarker(String key) {
        String local = localCache.getIfPresent(key);
        if (local != null) {
            return local.isEmpty();
        }
        String json = redis.opsForValue().get(key);
        return json != null && json.isEmpty();
    }

    /* ==================== 读 ==================== */

    /**
     * 防穿透：查不到时写入空值（短 TTL），避免同一个不存在的 id 反复打数据库
     */
    public <R> R queryWithPassThrough(String key, Class<R> type, Function<String, R> loader,
                                      long ttl, TimeUnit unit) {
        String json = redis.opsForValue().get(key);
        if (StringUtils.hasText(json)) {
            return fromJson(json, type);
        }
        // json 非 null 但为空串 → 命中空值缓存，直接返回 null
        if (json != null) {
            return null;
        }

        R value = loader.apply(key);
        if (value == null) {
            // 空值也缓存，但 TTL 要短：既挡住穿透，又不会让后来新增的数据长时间不可见
            redis.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL_MINUTES, TimeUnit.MINUTES);
            return null;
        }
        set(key, value, ttl, unit);
        return value;
    }

    /**
     * 防击穿：逻辑过期 + 互斥锁异步重建
     * <p>
     * 缓存命中就直接返回（哪怕已逻辑过期），只有一个线程去重建，
     * 用户永远不会被阻塞在数据库查询上。
     */
    public <R> R queryWithLogicalExpire(String key, Class<R> type, Function<String, R> loader,
                                        long ttl, TimeUnit unit) {
        String json = localCache.getIfPresent(key);
        if (!StringUtils.hasText(json)) {
            json = redis.opsForValue().get(key);
        }
        if (!StringUtils.hasText(json)) {
            // 逻辑过期方案依赖缓存预热，未命中说明是冷数据，交给调用方回源
            return null;
        }

        RedisData redisData = fromJson(json, RedisData.class);
        if (redisData == null) {
            return null;
        }
        R value = objectMapper.convertValue(redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime != null && expireTime.isAfter(LocalDateTime.now())) {
            localCache.put(key, json);
            return value;
        }

        // 已逻辑过期：抢到锁的线程异步重建，其他线程直接返回旧值
        String lockKey = RedisConstants.LOCK_REBUILD_KEY + key;
        if (tryLock(lockKey, 10)) {
            REBUILD_EXECUTOR.submit(() -> {
                try {
                    R fresh = loader.apply(key);
                    if (fresh != null) {
                        setWithLogicalExpire(key, fresh, ttl, unit);
                    }
                } catch (Exception e) {
                    log.error("缓存重建失败: {}", key, e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        return value;
    }

    /* ==================== 缓存重建锁 ==================== */

    /**
     * 简单的分布式互斥锁（SET NX EX 一条命令完成，避免 SETNX 后宕机导致死锁）
     * <p>
     * 生产环境可直接换成 Redisson。这里只是缓存重建用，对锁的要求没那么高。
     */
    public boolean tryLock(String key, long seconds) {
        Boolean ok = redis.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(seconds));
        return Boolean.TRUE.equals(ok);
    }

    public void unlock(String key) {
        redis.delete(key);
    }

    /* ==================== JSON ==================== */

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException("缓存序列化失败");
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("缓存反序列化失败，按未命中处理: {}", json, e);
            return null;
        }
    }
}
