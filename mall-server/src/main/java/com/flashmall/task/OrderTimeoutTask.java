package com.flashmall.task;

import com.flashmall.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时未支付关单任务
 * <p>
 * 每分钟跑一次。多实例部署时会同时触发，但关单动作走状态机 CAS，
 * 只有一个实例能成功，其余实例拿到 0 行直接返回，不需要额外加分布式锁。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    private final OrderService orderService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void scanTimeoutOrders() {
        try {
            int count = orderService.scanTimeoutOrders();
            if (count > 0) {
                log.info("本轮超时关单 {} 笔", count);
            }
        } catch (Exception e) {
            log.error("超时关单任务异常", e);
        }
    }
}
