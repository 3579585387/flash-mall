package com.flashmall.controller;

import com.flashmall.common.result.Result;
import com.flashmall.pojo.vo.SeckillResultVO;
import com.flashmall.seckill.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端秒杀
 * <p>
 * 抢购接口不落库：执行一次 Lua 脚本就返回 orderNo，
 * 前端拿着 orderNo 轮询 /result/{orderNo} 拿最终结果。
 */
@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    /**
     * 抢购
     */
    @PostMapping("/{activityId}/{skuId}")
    public Result<SeckillResultVO> seckill(@PathVariable Long activityId, @PathVariable Long skuId) {
        return Result.ok(seckillService.seckill(activityId, skuId));
    }

    /**
     * 查询抢购结果：PROCESSING / SUCCESS / FAIL
     */
    @GetMapping("/result/{orderNo}")
    public Result<SeckillResultVO> result(@PathVariable String orderNo) {
        return Result.ok(seckillService.queryResult(orderNo));
    }
}
