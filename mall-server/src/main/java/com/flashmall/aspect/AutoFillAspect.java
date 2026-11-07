package com.flashmall.aspect;

import com.flashmall.annotation.AutoFill;
import com.flashmall.common.constant.FieldAutoFillConstant;
import com.flashmall.common.enums.OperationType;
import com.flashmall.common.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 公共字段自动填充切面
 * <p>
 * 解决了什么问题：几乎每张业务表都有 create_time / update_time / create_user /
 * update_user，如果每个 Service 手写一遍，重复且容易漏。用「注解 + AOP + 反射」
 * 统一处理，业务代码只关心业务字段。
 * <p>
 * 用 getMethod 而不是 getDeclaredMethod：实体都是 public class + Lombok 生成的
 * public setter，用 getMethod 可以兼容父类上的字段。
 */
@Slf4j
@Aspect
@Component
public class AutoFillAspect {

    @Pointcut("@annotation(com.flashmall.annotation.AutoFill)")
    public void autoFillPointCut() {
    }

    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class);
        OperationType operationType = autoFill.value();

        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0 || args[0] == null) {
            return;
        }
        Object entity = args[0];

        LocalDateTime now = LocalDateTime.now();
        Long currentUserId = UserHolder.getUserId();

        if (operationType == OperationType.INSERT) {
            invoke(entity, FieldAutoFillConstant.SET_CREATE_TIME, LocalDateTime.class, now);
            invoke(entity, FieldAutoFillConstant.SET_CREATE_USER, Long.class, currentUserId);
            invoke(entity, FieldAutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class, now);
            invoke(entity, FieldAutoFillConstant.SET_UPDATE_USER, Long.class, currentUserId);
        } else if (operationType == OperationType.UPDATE) {
            invoke(entity, FieldAutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class, now);
            invoke(entity, FieldAutoFillConstant.SET_UPDATE_USER, Long.class, currentUserId);
        }
    }

    /**
     * 反射赋值。实体上可能没有这个字段（比如订单表没有 create_user），
     * 找不到 setter 时静默跳过，不影响业务。
     */
    private void invoke(Object entity, String setterName, Class<?> paramType, Object value) {
        try {
            Method setter = entity.getClass().getMethod(setterName, paramType);
            setter.invoke(entity, value);
        } catch (NoSuchMethodException e) {
            // 该实体没有这个公共字段，跳过
        } catch (Exception e) {
            log.warn("公共字段自动填充失败: {}.{}", entity.getClass().getSimpleName(), setterName, e);
        }
    }
}
