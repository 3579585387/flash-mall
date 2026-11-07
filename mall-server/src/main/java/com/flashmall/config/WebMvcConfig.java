package com.flashmall.config;

import com.flashmall.interceptor.AdminLoginInterceptor;
import com.flashmall.interceptor.TokenRefreshInterceptor;
import com.flashmall.interceptor.UserLoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 拦截器注册
 * <p>
 * 三个拦截器分三层，顺序很重要：
 * 1. TokenRefreshInterceptor：任何请求都尝试解析 token 并写入 ThreadLocal（不拦截）；
 * 2. AdminLoginInterceptor：B 端接口校验「已登录 + 是商家」；
 * 3. UserLoginInterceptor：C 端接口校验「已登录」。
 * <p>
 * 这样拆的好处：登录态读取与登录校验解耦，哪个接口要登录只是多挂一个拦截器而已。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final TokenRefreshInterceptor tokenRefreshInterceptor;
    private final UserLoginInterceptor userLoginInterceptor;
    private final AdminLoginInterceptor adminLoginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenRefreshInterceptor)
                .addPathPatterns("/**")
                .order(0);

        registry.addInterceptor(adminLoginInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/auth/login")
                .order(1);

        registry.addInterceptor(userLoginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",           // 登录、发验证码
                        "/api/admin/auth/login",  // 后台登录
                        "/api/goods/**",          // 商品浏览对游客开放
                        "/api/health",            // 环境自检
                        "/api/pay/notify",        // 支付回调（第三方调用，无 token）
                        "/error")
                .order(2);
    }
}
