package com.flashmall.aspect;

import com.flashmall.annotation.RateLimit;
import com.flashmall.common.constant.MessageConstant;
import com.flashmall.common.constant.RedisConstants;
import com.flashmall.common.exception.BizException;
import com.flashmall.common.utils.SpElUtil;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * 接口限流切面（固定窗口计数器）
 * <p>
 * 优点：实现简单、一条 INCR 就能挡住大部分刷接口行为；
 * 缺点：临界点会有双倍突发流量。生产环境换成 Redisson RRateLimiter（令牌桶）或 Sentinel。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate redis;

    @Around("@annotation(com.flashmall.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        String bizKey = SpElUtil.parse(rateLimit.key(), method, joinPoint.getArgs());
        String key = RedisConstants.LIMIT_KEY + method.getDeclaringClass().getSimpleName()
                + ":" + method.getName() + ":" + bizKey;

        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(rateLimit.seconds()));
        }
        if (count != null && count > rateLimit.count()) {
            throw new BizException(MessageConstant.TOO_MANY_REQUESTS);
        }
        return joinPoint.proceed();
    }
}
