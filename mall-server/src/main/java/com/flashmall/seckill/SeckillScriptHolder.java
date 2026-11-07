package com.flashmall.seckill;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 秒杀相关的 Lua 脚本集中管理
 * <p>
 * 为什么 XGROUP / XACK 也用 Lua 而不是 Java API：
 * 1. 少一层 API 版本兼容问题（Spring Data Redis 各版本 Stream API 有过调整）；
 * 2. XGROUP CREATE 在消费组已存在时会报 BUSYGROUP，用 Lua 的 pcall 可以直接吞掉这个错误，
 * 代码比 Java 里 try-catch 更干净。
 */
public class SeckillScriptHolder {

    private SeckillScriptHolder() {
    }

    /** 秒杀主脚本：活动校验 + 库存扣减 + 一人一单 + 消息投递 */
    public static final DefaultRedisScript<Long> SECKILL_SCRIPT =
            fromClasspath("lua/seckill.lua");

    /** 失败回补脚本：把 Redis 库存还回去、清掉售罄标记 */
    public static final DefaultRedisScript<Long> ROLLBACK_SCRIPT =
            fromClasspath("lua/rollback.lua");

    /** 创建消费组（MKSTREAM 保证 stream 不存在时也能创建），已存在时静默跳过 */
    public static final DefaultRedisScript<Long> CREATE_GROUP_SCRIPT = new DefaultRedisScript<>(
            "redis.pcall('xgroup','create',KEYS[1],ARGV[1],'0','MKSTREAM'); return 1", Long.class);

    /** 确认消息（消费成功后调用，失败的消息会留在 PENDING 列表里） */
    public static final DefaultRedisScript<Long> ACK_SCRIPT = new DefaultRedisScript<>(
            "return redis.call('xack', KEYS[1], ARGV[1], ARGV[2])", Long.class);

    private static DefaultRedisScript<Long> fromClasspath(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
