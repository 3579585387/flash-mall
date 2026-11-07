package com.flashmall.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Properties;

/**
 * Redis 版本探测工具
 * <p>
 * 为什么要这个：Redis Stream（XADD / XGROUP / XREADGROUP / XACK）是 **5.0** 才引入的，
 * 而网上很多 Windows 版 Redis 还是 3.x（比如课程资料里的 3.2.100）。
 * 在 3.x 上跑本项目的秒杀链路会报：ERR unknown command 'XREADGROUP'。
 * <p>
 * 与其让使用者对着一堆堆栈猜，不如启动时直接检测并说清楚。
 */
@Slf4j
public class RedisVersionUtil {

    private RedisVersionUtil() {
    }

    /**
     * 读取 INFO server 里的 redis_version，取不到返回 null
     */
    public static String getVersion(StringRedisTemplate redis) {
        try {
            Properties properties = redis.execute((RedisCallback<Properties>)
                    connection -> connection.serverCommands().info("server"));
            if (properties == null) {
                return null;
            }
            String version = properties.getProperty("redis_version");
            return version == null ? null : version.trim();
        } catch (Exception e) {
            log.warn("读取 Redis 版本失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 取主版本号，例如 "3.2.100" -> 3，"7.2.4" -> 7；解析失败返回 -1
     */
    public static int major(String version) {
        if (version == null || version.isEmpty()) {
            return -1;
        }
        int dot = version.indexOf('.');
        String head = dot < 0 ? version : version.substring(0, dot);
        try {
            return Integer.parseInt(head.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 是否支持 Redis Stream（需要 5.0+）
     */
    public static boolean supportsStream(StringRedisTemplate redis) {
        return major(getVersion(redis)) >= 5;
    }
}
