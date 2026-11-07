package com.flashmall.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson 配置（分布式锁）
 * <p>
 * 这里直接使用 redisson 依赖 + 手工构造 RedissonClient，
 * 而不是 redisson-spring-boot-starter，避免 Spring Data Redis 版本对齐问题。
 * <p>
 * 默认关闭：当前业务代码没有直接注入 RedissonClient。保持关闭可以避免
 * "Redis 没启动 → Redisson.create() 抛异常 → 整个应用无法启动"，
 * 也让 /api/health 的自检流程可用。需要真正使用 Redisson 时把
 * flash-mall.redisson.enabled 改为 true。
 */
@Configuration
@ConditionalOnProperty(name = "flash-mall.redisson.enabled", havingValue = "true")
public class RedissonConfig {

    @Value("${flash-mall.redisson.address:redis://127.0.0.1:6379}")
    private String address;

    @Value("${flash-mall.redisson.password:}")
    private String password;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress(address);
        if (StringUtils.hasText(password)) {
            config.useSingleServer().setPassword(password);
        }
        try {
            return Redisson.create(config);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Redisson 初始化失败，请确认 Redis 已启动且地址正确：" + address, e);
        }
    }
}
