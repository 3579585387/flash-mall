package com.flashmall.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket 配置：把 @ServerEndpoint 注解的 Bean 注册到容器
 * <p>
 * 注意：只有在使用内嵌容器（Tomcat）时才需要这个 Bean，
 * 如果打成 war 部署到外部 Tomcat，要把它去掉，否则会重复注册。
 */
@Configuration
public class WebSocketConfig {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
