package com.flashmall.annotation;

import com.flashmall.common.enums.OperationType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 公共字段自动填充
 * <p>
 * 用法：打在 Service 方法上，方法的第一个参数必须是实体对象
 * <pre>
 *     &#64;AutoFill(OperationType.INSERT)
 *     public void saveGoods(Goods goods) { ... }
 * </pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoFill {

    /** 数据库操作类型 */
    OperationType value();
}
