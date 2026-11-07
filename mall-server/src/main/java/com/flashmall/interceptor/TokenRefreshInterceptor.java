package com.flashmall.interceptor;

import com.flashmall.common.constant.RedisConstants;
import com.flashmall.common.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 登录态解析 + 滑动续期
 * <p>
 * 这个拦截器永远返回 true，只负责"如果有 token 就把用户写进 ThreadLocal"，
 * 是否需要登录交给后面的拦截器判断。
 * <p>
 * 滑动续期：只要用户还在操作，就把 token 的有效期续回 30 分钟，
 * 避免用户聊到一半被踢下线；同时剩余时间低于 10 分钟才续期，
 * 不是每个请求都写 Redis，降低写压力。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenRefreshInterceptor implements HandlerInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final StringRedisTemplate redis;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader(AUTH_HEADER);
        if (!StringUtils.hasText(token)) {
            // 兼容 WebSocket / 调试场景：允许通过 ?token=xxx 传递
            token = request.getParameter("token");
        }
        if (!StringUtils.hasText(token)) {
            return true;
        }
        if (token.startsWith(BEARER_PREFIX)) {
            token = token.substring(BEARER_PREFIX.length());
        }

        String key = RedisConstants.LOGIN_TOKEN_KEY + token;
        Map<Object, Object> entries = redis.opsForHash().entries(key);
        if (entries == null || entries.isEmpty() || entries.get("id") == null) {
            return true;
        }

        Long userId = Long.valueOf(String.valueOf(entries.get("id")));
        Object typeValue = entries.get("type");
        Integer userType = typeValue == null
                ? RedisConstants.USER_TYPE_USER
                : Integer.valueOf(String.valueOf(typeValue));
        UserHolder.set(userId, userType);

        // 滑动续期
        Long ttl = redis.getExpire(key, TimeUnit.MINUTES);
        if (ttl != null && ttl >= 0 && ttl < RedisConstants.LOGIN_TOKEN_REFRESH_MINUTES) {
            redis.expire(key, Duration.ofMinutes(RedisConstants.LOGIN_TOKEN_TTL_MINUTES));
        }
        return true;
    }

    /**
     * 必须清理 ThreadLocal：Tomcat 线程池会复用线程，
     * 不清会把上一个请求的登录用户带到下一个请求里（隐蔽的越权 bug）。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserHolder.remove();
    }
}
