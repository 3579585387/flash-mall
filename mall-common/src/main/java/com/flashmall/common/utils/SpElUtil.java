package com.flashmall.common.utils;

import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * SpEL 解析工具：把注解里的 "#skuId" 解析成真实的参数值
 * <p>
 * 依赖 maven 编译参数 -parameters（spring-boot-starter-parent 默认开启），
 * 否则拿不到真实参数名。
 */
public class SpElUtil {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer DISCOVERER = new DefaultParameterNameDiscoverer();

    private SpElUtil() {
    }

    public static String parse(String expression, Method method, Object[] args) {
        if (!StringUtils.hasText(expression)) {
            return "";
        }
        try {
            MethodBasedEvaluationContext context =
                    new MethodBasedEvaluationContext(null, method, args, DISCOVERER);
            Object value = PARSER.parseExpression(expression).getValue(context);
            return value == null ? "" : value.toString();
        } catch (Exception e) {
            // 解析失败不能影响主流程，退化为"无自定义 key"
            return "";
        }
    }
}
