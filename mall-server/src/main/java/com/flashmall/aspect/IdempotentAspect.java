package com.flashmall.aspect;

import com.flashmall.annotation.Idempotent;
import com.flashmall.common.constant.MessageConstant;
import com.flashmall.common.constant.RedisConstants;
import com.flashmall.common.exception.BizException;
import com.flashmall.common.utils.SpElUtil;
import com.flashmall.common.utils.UserHolder;
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
 * 接口幂等切面：Redis SETNX 抢占，抢不到的请求直接拒绝
 * <p>
 * 注意：业务执行失败时要释放 key，否则用户在这段时间内无法重试。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private final StringRedisTemplate redis;

    @Around("@annotation(com.flashmall.annotation.Idempotent)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);

        String bizKey = SpElUtil.parse(idempotent.key(), method, joinPoint.getArgs());
        // 默认按「用户 + 方法 + 业务参数」隔离，避免不同用户互相误伤，也避免同一用户
        // 改一个业务参数（比如换收货手机号）就绕过幂等窗口。
        Long userId = UserHolder.getUserId();
        String userPart = userId == null ? "anonymous" : String.valueOf(userId);
        String key = RedisConstants.IDEMPOTENT_KEY
                + method.getDeclaringClass().getSimpleName() + ":" + method.getName()
                + ":" + userPart + ":" + bizKey;

        Boolean ok = redis.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(idempotent.timeout()));
        if (!Boolean.TRUE.equals(ok)) {
            throw new BizException(MessageConstant.REPEAT_SUBMIT);
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            // 业务失败，放开幂等窗口让用户可以重试
            redis.delete(key);
            throw e;
        }
    }
}
