package com.flashmall.runner;

import com.flashmall.seckill.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时预热所有未结束的秒杀活动
 * <p>
 * 为什么要在启动时预热：逻辑过期和售罄拦截都依赖 Redis 里已有数据，
 * 如果活动开始那一刻缓存是空的，第一批请求会全部打到数据库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class SeckillPreheatRunner implements ApplicationRunner {

    private final SeckillService seckillService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            seckillService.preheatAll();
        } catch (Exception e) {
            log.error("启动预热失败（不影响应用启动）", e);
        }
    }
}
