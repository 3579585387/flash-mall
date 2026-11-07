package com.flashmall.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口幂等（基于 Redis SETNX）
 * <p>
 * 用法：打在 Service 方法上，key 支持 SpEL，例如
 * <pre>
 *     &#64;Idempotent(key = "#skuId", timeout = 5)
 * </pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** 业务 key 表达式（SpEL），为空时按「用户 + 方法」维度加锁 */
    String key() default "";

    /** 幂等窗口（秒） */
    long timeout() default 5L;
}
