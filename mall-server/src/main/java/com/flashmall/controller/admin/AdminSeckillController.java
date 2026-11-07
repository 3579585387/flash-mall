package com.flashmall.controller.admin;

import com.flashmall.common.result.Result;
import com.flashmall.pojo.dto.SeckillPublishDTO;
import com.flashmall.pojo.entity.SeckillActivity;
import com.flashmall.seckill.SeckillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * B 端秒杀活动管理
 * <p>
 * 发布活动时会自动把库存、限购、起止时间预热到 Redis，
 * 抢购接口因此不需要访问数据库。
 */
@RestController
@RequestMapping("/api/admin/seckill")
@RequiredArgsConstructor
public class AdminSeckillController {

    private final SeckillService seckillService;

    /**
     * 发布秒杀活动
     */
    @PostMapping("/publish")
    public Result<Long> publish(@RequestBody @Valid SeckillPublishDTO dto) {
        return Result.ok(seckillService.publish(dto));
    }

    /**
     * 手动重新预热
     * <p>
     * 默认只补缺失的 key，不覆盖 Redis 中已有的库存；确实要按 DB 库存重置时传 force=true，
     * 但活动进行中调用 force=true 可能把尚未消费的消息已经扣掉的库存还原，需谨慎。
     */
    @PostMapping("/{activityId}/preheat")
    public Result<Void> preheat(@PathVariable Long activityId,
                                @RequestParam(defaultValue = "false") boolean force) {
        seckillService.preheatActivity(activityId, force);
        return Result.ok();
    }

    @GetMapping("/list")
    public Result<List<SeckillActivity>> list() {
        return Result.ok(seckillService.listActivities());
    }

    /**
     * 活动详情：同时返回 DB 库存和 Redis 剩余库存，方便对账
     */
    @GetMapping("/{activityId}")
    public Result<Map<String, Object>> detail(@PathVariable Long activityId) {
        return Result.ok(seckillService.activityDetail(activityId));
    }

    /**
     * 取一个还有库存的秒杀商品，压测脚本可以用它拿 activityId / skuId
     */
    @GetMapping("/current")
    public Result<Map<String, Object>> current(@RequestParam(required = false) Long activityId) {
        return Result.ok(seckillService.currentSeckill(activityId));
    }
}
