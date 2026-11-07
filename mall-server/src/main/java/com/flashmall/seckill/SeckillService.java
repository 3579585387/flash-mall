package com.flashmall.seckill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashmall.annotation.RateLimit;
import com.flashmall.common.constant.MessageConstant;
import com.flashmall.common.constant.RedisConstants;
import com.flashmall.common.enums.ActivityStatus;
import com.flashmall.common.exception.BizException;
import com.flashmall.common.utils.UserHolder;
import com.flashmall.utils.RedisIdWorker;
import com.flashmall.mapper.GoodsMapper;
import com.flashmall.mapper.GoodsSkuMapper;
import com.flashmall.mapper.SeckillActivityMapper;
import com.flashmall.mapper.SeckillSkuMapper;
import com.flashmall.pojo.dto.SeckillPublishDTO;
import com.flashmall.pojo.entity.Goods;
import com.flashmall.pojo.entity.GoodsSku;
import com.flashmall.pojo.entity.SeckillActivity;
import com.flashmall.pojo.entity.SeckillSku;
import com.flashmall.pojo.vo.SeckillResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 秒杀
 * <p>
 * 抢购接口只做一件事：执行一次 Lua 脚本。脚本内部完成
 * 「活动校验 → 库存扣减 → 一人一单 → 消息投递」，Redis 单线程执行保证原子性，
 * 主线程不碰数据库，因此接口耗时在毫秒级，吞吐可以做得很高。
 * 真正的落库由 {@link SeckillOrderConsumer} 异步完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    /** 活动时间与 Redis TIME 对齐用：本项目按东八区解释 LocalDateTime */
    private static final ZoneOffset ZONE_OFFSET = ZoneOffset.ofHours(8);

    private final StringRedisTemplate redis;
    private final SeckillActivityMapper activityMapper;
    private final SeckillSkuMapper seckillSkuMapper;
    private final GoodsMapper goodsMapper;
    private final GoodsSkuMapper goodsSkuMapper;
    private final RedisIdWorker idWorker;

    /* ==================== 用户抢购 ==================== */

    /**
     * 抢购
     * <p>
     * 加了一个很宽松的全局限流（每秒 2 万），只作为"熔断保险丝"，
     * 正常情况下不会触发，但 Redis 或消费者整体挂掉时能挡住无意义的流量。
     */
    @RateLimit(count = 20000, seconds = 1)
    public SeckillResultVO seckill(Long activityId, Long skuId) {
        Long userId = UserHolder.requireUserId();
        // 和普通订单共用同一个 Redis 序列 key，避免两个前缀在同一秒生成相同 ID
        String orderNo;
        try {
            orderNo = String.valueOf(idWorker.nextId("order"));
        } catch (Exception e) {
            log.error("生成秒杀订单号失败 activityId={}, skuId={}", activityId, skuId, e);
            throw new BizException(MessageConstant.SECKILL_BUSY);
        }

        Long result;
        try {
            result = redis.execute(SeckillScriptHolder.SECKILL_SCRIPT, Collections.emptyList(),
                    String.valueOf(activityId),
                    String.valueOf(skuId),
                    String.valueOf(userId),
                    orderNo,
                    RedisConstants.SECKILL_RESULT_KEY + orderNo,
                    String.valueOf(RedisConstants.SECKILL_RESULT_TTL_MINUTES * 60));
        } catch (Exception e) {
            // Redis 不可用时快速失败，绝不能把流量放给数据库
            log.error("秒杀脚本执行异常 activityId={}, skuId={}", activityId, skuId, e);
            throw new BizException(MessageConstant.SECKILL_BUSY);
        }

        int code = result == null ? -1 : result.intValue();
        switch (code) {
            case 0:
                // PROCESSING 已由 Lua 脚本原子写入，消费者不可能先写 SUCCESS 再被覆盖
                log.debug("用户 {} 抢购成功，orderNo={}", userId, orderNo);
                return new SeckillResultVO(orderNo, RedisConstants.SECKILL_RESULT_PROCESSING,
                        "抢购成功，订单正在创建");
            case 1:
                throw new BizException(MessageConstant.SECKILL_SOLD_OUT);
            case 2:
                throw new BizException(MessageConstant.SECKILL_LIMIT);
            case 3:
                throw new BizException(MessageConstant.SECKILL_NOT_START);
            case 4:
                throw new BizException(MessageConstant.SECKILL_ENDED);
            default:
                throw new BizException(MessageConstant.SECKILL_BUSY);
        }
    }

    /**
     * 查询抢购结果（前端拿到 orderNo 后轮询这个接口）
     */
    public SeckillResultVO queryResult(String orderNo) {
        String status = redis.opsForValue().get(RedisConstants.SECKILL_RESULT_KEY + orderNo);
        if (status == null) {
            return new SeckillResultVO(orderNo, RedisConstants.SECKILL_RESULT_FAIL, "结果不存在或已过期");
        }
        if (RedisConstants.SECKILL_RESULT_SUCCESS.equals(status)) {
            return new SeckillResultVO(orderNo, status, "抢购成功，请尽快支付");
        }
        if (RedisConstants.SECKILL_RESULT_PROCESSING.equals(status)) {
            return new SeckillResultVO(orderNo, status, "订单创建中，请稍后刷新");
        }
        return new SeckillResultVO(orderNo, RedisConstants.SECKILL_RESULT_FAIL, "抢购失败，名额已释放");
    }

    /* ==================== 活动发布与预热 ==================== */

    @Transactional(rollbackFor = Exception.class)
    public Long publish(SeckillPublishDTO dto) {
        if (!dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new BizException("结束时间必须晚于开始时间");
        }

        SeckillActivity activity = new SeckillActivity();
        activity.setTitle(dto.getTitle());
        activity.setStartTime(dto.getStartTime());
        activity.setEndTime(dto.getEndTime());
        activity.setStatus(ActivityStatus.ONGOING.getCode());
        activity.setCreateTime(LocalDateTime.now());
        activityMapper.insert(activity);

        for (SeckillPublishDTO.Item item : dto.getItems()) {
            GoodsSku sku = goodsSkuMapper.selectById(item.getSkuId());
            if (sku == null) {
                throw new BizException("SKU 不存在: " + item.getSkuId());
            }
            Goods goods = goodsMapper.selectById(sku.getGoodsId());
            if (goods == null) {
                throw new BizException(MessageConstant.GOODS_NOT_FOUND);
            }
            int realStock = sku.getStock() == null ? 0 : sku.getStock();
            if (item.getSeckillStock() > realStock) {
                throw new BizException("秒杀库存不能超过 SKU 真实库存: skuId=" + item.getSkuId());
            }

            SeckillSku seckillSku = new SeckillSku();
            seckillSku.setActivityId(activity.getId());
            seckillSku.setSkuId(sku.getId());
            seckillSku.setGoodsId(goods.getId());
            seckillSku.setSeckillPrice(item.getSeckillPrice());
            seckillSku.setSeckillStock(item.getSeckillStock());
            int limit = item.getLimitPerUser() == null || item.getLimitPerUser() < 1
                    ? 1 : item.getLimitPerUser();
            seckillSku.setLimitPerUser(limit);
            seckillSku.setVersion(0);
            seckillSkuMapper.insert(seckillSku);
        }

        // 发布后立即预热，避免活动开始时第一次请求还要查库
        doPreheat(activity, true);
        log.info("秒杀活动发布成功 id={}, title={}", activity.getId(), activity.getTitle());
        return activity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void preheatActivity(Long activityId) {
        preheatActivity(activityId, false);
    }

    /**
     * 手动预热
     *
     * @param force true 表示用 DB 库存覆盖 Redis 库存（仅适用于活动未开始或确认没有在途消息时，
     *              运行中的活动请保持 false，否则会把尚未消费的消息已经扣掉的库存还原回去）
     */
    @Transactional(rollbackFor = Exception.class)
    public void preheatActivity(Long activityId, boolean force) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException("活动不存在");
        }
        doPreheat(activity, force);
    }

    /**
     * 应用启动时预热所有未结束的活动
     * <p>
     * 数据库里的 seckill_stock 才是真相，Redis 只是它的镜像，
     * 所以重启后重新预热不会导致库存错乱。
     */
    public void preheatAll() {
        List<SeckillActivity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<SeckillActivity>()
                        .gt(SeckillActivity::getEndTime, LocalDateTime.now()));
        for (SeckillActivity activity : activities) {
            try {
                // 启动预热不覆盖已有库存：Stream 里可能还有已扣 Redis、未落 DB 的消息
                doPreheat(activity, false);
            } catch (Exception e) {
                log.error("活动预热失败 id={}", activity.getId(), e);
            }
        }
        log.info("秒杀活动预热完成，共 {} 场", activities.size());
    }

    private void doPreheat(SeckillActivity activity, boolean force) {
        Long activityId = activity.getId();

        // key 的有效期覆盖到活动结束后 1 小时：既保证活动期间数据在，也不会无限堆积
        Duration ttl = Duration.between(LocalDateTime.now(), activity.getEndTime()).plusHours(1);
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofHours(1);
        }

        redis.opsForValue().set(RedisConstants.SECKILL_BEGIN_KEY + activityId,
                String.valueOf(activity.getStartTime().toEpochSecond(ZONE_OFFSET)), ttl);
        redis.opsForValue().set(RedisConstants.SECKILL_END_KEY + activityId,
                String.valueOf(activity.getEndTime().toEpochSecond(ZONE_OFFSET)), ttl);

        List<SeckillSku> skus = seckillSkuMapper.selectList(new LambdaQueryWrapper<SeckillSku>()
                .eq(SeckillSku::getActivityId, activityId));

        for (SeckillSku seckillSku : skus) {
            Long skuId = seckillSku.getSkuId();
            int stock = seckillSku.getSeckillStock() == null ? 0 : seckillSku.getSeckillStock();
            int limit = seckillSku.getLimitPerUser() == null || seckillSku.getLimitPerUser() < 1
                    ? 1 : seckillSku.getLimitPerUser();

            String stockKey = RedisConstants.seckillStockKey(activityId, skuId);
            String limitKey = RedisConstants.seckillLimitKey(activityId, skuId);
            String userKey = RedisConstants.seckillUserKey(activityId, skuId);
            String soldoutKey = RedisConstants.seckillSoldoutKey(activityId, skuId);

            if (force) {
                // 发布新活动 / 明确要求重置时，才用 DB 库存覆盖
                redis.opsForValue().set(stockKey, String.valueOf(Math.max(stock, 0)), ttl);
            } else {
                // 关键：不要覆盖已有库存！
                // 否则应用重启时，Stream 中尚未消费的消息已经扣掉的 Redis 库存会被还原，
                // 造成 Redis 多卖、DB 条件更新失败、回补脚本反复抖动的连锁问题。
                redis.opsForValue().setIfAbsent(stockKey, String.valueOf(Math.max(stock, 0)), ttl);
                redis.expire(stockKey, ttl);
            }
            redis.opsForValue().set(limitKey, String.valueOf(limit), ttl);

            // 关键：不要 delete userKey！
            // 否则应用重启后，"一人一单"的限制会被重置，用户能重复抢购。
            redis.expire(userKey, ttl);

            // 售罄标记以 Redis 里的实际库存为准，而不是 DB 库存
            int effectiveStock = stock;
            String currentStock = redis.opsForValue().get(stockKey);
            if (currentStock != null) {
                try {
                    effectiveStock = Integer.parseInt(currentStock);
                } catch (NumberFormatException e) {
                    log.warn("库存 key 值非法 activityId={}, skuId={}, value={}",
                            activityId, skuId, currentStock);
                }
            }
            if (effectiveStock <= 0) {
                redis.opsForValue().set(soldoutKey, "1", ttl);
            } else {
                redis.delete(soldoutKey);
            }
        }
        log.info("活动 {} 预热完成，秒杀商品 {} 个（force={}）", activityId, skus.size(), force);
    }

    /* ==================== 后台查询 ==================== */

    public List<SeckillActivity> listActivities() {
        return activityMapper.selectList(new LambdaQueryWrapper<SeckillActivity>()
                .orderByDesc(SeckillActivity::getId));
    }

    /**
     * 活动详情：包含每件秒杀商品的「数据库库存」与「Redis 剩余库存」，
     * 两边对不上就说明有消息还没消费完或者出了问题，方便排查。
     */
    public Map<String, Object> activityDetail(Long activityId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BizException("活动不存在");
        }
        List<SeckillSku> skus = seckillSkuMapper.selectList(new LambdaQueryWrapper<SeckillSku>()
                .eq(SeckillSku::getActivityId, activityId));

        List<Map<String, Object>> items = new ArrayList<>(skus.size());
        for (SeckillSku seckillSku : skus) {
            GoodsSku sku = goodsSkuMapper.selectById(seckillSku.getSkuId());
            Goods goods = goodsMapper.selectById(seckillSku.getGoodsId());
            String redisStock = redis.opsForValue().get(
                    RedisConstants.seckillStockKey(activityId, seckillSku.getSkuId()));

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("skuId", seckillSku.getSkuId());
            item.put("goodsName", goods == null ? "" : goods.getName());
            item.put("specJson", sku == null ? "" : sku.getSpecJson());
            item.put("seckillPrice", seckillSku.getSeckillPrice());
            item.put("limitPerUser", seckillSku.getLimitPerUser());
            item.put("dbStock", seckillSku.getSeckillStock());
            item.put("redisStock", redisStock == null ? "未预热" : redisStock);
            items.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activityId", activity.getId());
        result.put("title", activity.getTitle());
        result.put("startTime", activity.getStartTime());
        result.put("endTime", activity.getEndTime());
        result.put("items", items);
        return result;
    }

    /** 供压测脚本获取一个可用的活动+商品组合 */
    public Map<String, Object> currentSeckill(Long activityId) {
        List<SeckillSku> skus = seckillSkuMapper.selectList(new LambdaQueryWrapper<SeckillSku>()
                .eq(activityId != null, SeckillSku::getActivityId, activityId)
                .gt(SeckillSku::getSeckillStock, 0)
                .last("limit 1"));

        Map<String, Object> result = new LinkedHashMap<>();
        if (skus.isEmpty()) {
            result.put("tip", "没有进行中的秒杀商品，请先调用后台接口发布活动");
            return result;
        }
        SeckillSku seckillSku = skus.get(0);
        SeckillActivity activity = activityMapper.selectById(seckillSku.getActivityId());
        result.put("activityId", seckillSku.getActivityId());
        result.put("skuId", seckillSku.getSkuId());
        result.put("title", activity == null ? "" : activity.getTitle());
        result.put("seckillPrice", seckillSku.getSeckillPrice() == null
                ? BigDecimal.ZERO : seckillSku.getSeckillPrice());
        result.put("dbStock", seckillSku.getSeckillStock());
        result.put("redisStock", redis.opsForValue().get(
                RedisConstants.seckillStockKey(seckillSku.getActivityId(), seckillSku.getSkuId())));
        return result;
    }
}
