package com.flashmall.config;

import com.flashmall.utils.RedisVersionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时检查 Redis 版本
 * <p>
 * 本项目有几处能力依赖 Redis 的具体版本：
 * <ul>
 *   <li>Redis Stream（XADD / XGROUP / XREADGROUP / XACK）—— <b>需要 5.0+</b>，秒杀异步下单的核心</li>
 *   <li>SET NX EX 一条命令实现互斥锁 —— 需要 2.6.12+</li>
 *   <li>GEO / BITFIELD 等 —— 需要 3.2+</li>
 * </ul>
 * 版本不够时直接把原因和解决办法打出来，避免使用者对着
 * "ERR unknown command 'XREADGROUP'" 这类报错排查半天。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(0)
public class RedisVersionChecker implements ApplicationRunner {

    private final StringRedisTemplate redis;

    @Override
    public void run(ApplicationArguments args) {
        String version = RedisVersionUtil.getVersion(redis);
        if (version == null) {
            log.error("========================================================");
            log.error(" 连不上 Redis（默认 127.0.0.1:6379）");
            log.error(" 秒杀、缓存、登录态全部依赖 Redis，请先启动它");
            log.error("========================================================");
            return;
        }

        int major = RedisVersionUtil.major(version);
        if (major >= 5) {
            log.info("Redis 版本 {} —— 支持 Stream，秒杀异步下单可用", version);
            return;
        }

        log.error("");
        log.error("############################################################");
        log.error("  Redis 版本过低：当前 {} ，本项目需要 5.0 及以上", version);
        log.error("");
        log.error("  原因：秒杀链路用到 Redis Stream");
        log.error("        XADD / XGROUP / XREADGROUP / XACK 都是 5.0 才有的命令");
        log.error("        在 3.x 上会报：ERR unknown command 'XREADGROUP'");
        log.error("");
        log.error("  影响：秒杀抢购、异步下单不可用（商品、购物车、订单等其它功能正常）");
        log.error("");
        log.error("  解决：停掉旧 redis-server，换成 Redis 5.0+");
        log.error("        Windows 可直接运行工作区的 启动Redis.bat（自动下载 5.0.14）");
        log.error("############################################################");
        log.error("");
    }
}
