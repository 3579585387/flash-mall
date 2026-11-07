package com.flashmall.controller;

import com.flashmall.common.result.PageResult;
import com.flashmall.common.result.Result;
import com.flashmall.pojo.dto.OrderSubmitDTO;
import com.flashmall.pojo.entity.Orders;
import com.flashmall.pojo.vo.OrderSubmitVO;
import com.flashmall.pojo.vo.OrderVO;
import com.flashmall.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * C 端订单
 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 提交订单：把购物车整体下单（事务内扣库存 + 写订单 + 清购物车）
     */
    @PostMapping("/submit")
    public Result<OrderSubmitVO> submit(@RequestBody @Valid OrderSubmitDTO dto) {
        return Result.ok(orderService.submit(dto));
    }

    @GetMapping("/page")
    public Result<PageResult<Orders>> page(@RequestParam(required = false) Integer status,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size) {
        return Result.ok(orderService.myPage(status, page, size));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.ok(orderService.detail(orderNo));
    }

    /**
     * 模拟发起支付（真实项目返回微信支付参数）
     */
    @PostMapping("/{orderNo}/pay")
    public Result<Map<String, Object>> pay(@PathVariable String orderNo) {
        return Result.ok(orderService.mockPrepay(orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancel(@PathVariable String orderNo,
                               @RequestParam(defaultValue = "用户主动取消") String reason) {
        orderService.cancelByUser(orderNo, reason);
        return Result.ok();
    }
}
