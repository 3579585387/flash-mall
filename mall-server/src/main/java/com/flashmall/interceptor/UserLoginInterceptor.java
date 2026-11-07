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
 * C 端登录校验：未登录直接返回 401（HTTP 状态码保持 200，用业务码区分，前端处理更简单）
 */
@Slf4j
@Component
public class UserLoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (UserHolder.isLogin()) {
            return true;
        }
        writeJson(response, Result.UNAUTHORIZED, "请先登录");
        return false;
    }

    static void writeJson(HttpServletResponse response, int code, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + code + ",\"msg\":\"" + msg + "\",\"data\":null}");
    }
}
