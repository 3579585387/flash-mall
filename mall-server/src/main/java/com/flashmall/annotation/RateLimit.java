package com.flashmall.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流（Redis 计数器实现，固定窗口）
 * <p>
 * 生产环境建议换成 Redisson RRateLimiter（令牌桶）或 Sentinel，
 * 这里用计数器是为了让方案足够简单、可读。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 窗口内允许的最大请求数 */
    int count() default 20;

    /** 窗口大小（秒） */
    int seconds() default 1;

    /** 额外 key 片段（SpEL），为空时按方法维度限流 */
    String key() default "";
}
