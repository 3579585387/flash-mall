package com.flashmall.controller.admin;

import com.flashmall.common.enums.OrderStatus;
import com.flashmall.common.result.PageResult;
import com.flashmall.common.result.Result;
import com.flashmall.pojo.entity.Orders;
import com.flashmall.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * B 端订单履约
 * <p>
 * 状态流转全部走 CAS：两个店员同时点「接单」只有一个能成功，
 * 不会出现「已接单」被覆盖成「已派送」这种脏数据。
 */
@RestController
@RequestMapping("/api/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping("/page")
    public Result<PageResult<Orders>> page(@RequestParam(required = false) Integer status,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size) {
        return Result.ok(orderService.adminPage(status, page, size));
    }

    /**
     * 接单：待接单 → 已接单
     */
    @PutMapping("/{orderNo}/confirm")
    public Result<Void> confirm(@PathVariable String orderNo) {
        orderService.transit(orderNo, OrderStatus.TO_BE_CONFIRMED, OrderStatus.CONFIRMED);
        return Result.ok();
    }

    /**
     * 派送：已接单 → 派送中
     */
    @PutMapping("/{orderNo}/deliver")
    public Result<Void> deliver(@PathVariable String orderNo) {
        orderService.transit(orderNo, OrderStatus.CONFIRMED, OrderStatus.DELIVERING);
        return Result.ok();
    }

    /**
     * 完成：派送中 → 已完成
     */
    @PutMapping("/{orderNo}/complete")
    public Result<Void> complete(@PathVariable String orderNo) {
        orderService.transit(orderNo, OrderStatus.DELIVERING, OrderStatus.COMPLETED);
        return Result.ok();
    }
}
