package com.flashmall.interceptor;

import com.flashmall.common.result.Result;
import com.flashmall.common.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * B 端登录校验：必须已登录，且 token 类型是商家（type=2）
 * <p>
 * C 端 token 打不开后台接口，避免"普通用户直接调后台接口"的越权问题。
 */
@Slf4j
@Component
public class AdminLoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!UserHolder.isLogin()) {
            UserLoginInterceptor.writeJson(response, Result.UNAUTHORIZED, "请先登录");
            return false;
        }
        if (!UserHolder.isAdmin()) {
            log.warn("非商家账号尝试访问后台接口: {}", request.getRequestURI());
            UserLoginInterceptor.writeJson(response, Result.FORBIDDEN, "无访问权限");
            return false;
        }
        return true;
    }
}
