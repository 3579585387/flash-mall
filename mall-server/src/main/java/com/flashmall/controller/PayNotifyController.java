package com.flashmall.controller;

import com.flashmall.common.exception.BizException;
import com.flashmall.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付回调（模拟）
 * <p>
 * 真实项目这里是微信支付 V3 的回调入口，必须做三件事：
 * 1. 验签（防止有人伪造回调白拿商品）；
 * 2. 解密 + 金额校验（防止改价）；
 * 3. 幂等处理（微信会重复回调，非 200 会持续重试）。
 * <p>
 * 本项目用 /api/pay/notify 模拟，可以重复调用同一个 orderNo 来验证幂等。
 */
@Slf4j
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PayNotifyController {

    private final OrderService orderService;

    @PostMapping("/notify")
    public Map<String, String> notify(@RequestBody Map<String, String> body) {
        String orderNo = body.get("orderNo");
        String tradeNo = body.getOrDefault("tradeNo", "mock_trade_" + orderNo);
        Map<String, String> response = new LinkedHashMap<>(2);

        if (orderNo == null || orderNo.isBlank()) {
            response.put("code", "FAIL");
            response.put("message", "orderNo 不能为空");
            return response;
        }

        try {
            orderService.paySuccess(orderNo, tradeNo);
            response.put("code", "SUCCESS");
            response.put("message", "成功");
        } catch (BizException e) {
            // 订单不存在等业务问题：返回 FAIL，让第三方按自己的策略重试
            log.warn("支付回调处理失败 orderNo={}, reason={}", orderNo, e.getMessage());
            response.put("code", "FAIL");
            response.put("message", e.getMessage());
        }
        return response;
    }
}
