package com.flashmall;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FlashMall 闪购商城 —— 高并发电商秒杀平台
 * <p>
 * 启动前请确认：
 * 1. MySQL 已启动（连接信息见 application.yml，库不存在会自动创建）；
 * 2. Redis 已启动（127.0.0.1:6379）。
 */
@SpringBootApplication
@MapperScan("com.flashmall.mapper")
@EnableScheduling
public class FlashMallApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlashMallApplication.class, args);
    }
}
