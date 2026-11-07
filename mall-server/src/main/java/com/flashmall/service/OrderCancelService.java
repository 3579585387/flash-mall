package com.flashmall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.flashmall.common.constant.MessageConstant;
import com.flashmall.common.constant.RedisConstants;
import com.flashmall.common.enums.OrderStatus;
import com.flashmall.common.exception.BizException;
import com.flashmall.mapper.GoodsMapper;
import com.flashmall.mapper.GoodsSkuMapper;
import com.flashmall.mapper.OrderItemMapper;
import com.flashmall.mapper.OrderMapper;
import com.flashmall.mapper.SeckillSkuMapper;
import com.flashmall.mapper.SeckillUserCountMapper;
import com.flashmall.mapper.StockLogMapper;
import com.flashmall.pojo.entity.Goods;
import com.flashmall.pojo.entity.GoodsSku;
import com.flashmall.pojo.entity.OrderItem;
import com.flashmall.pojo.entity.Orders;
import com.flashmall.pojo.entity.SeckillSku;
import com.flashmall.pojo.entity.SeckillUserCount;
import com.flashmall.pojo.entity.StockLog;
import com.flashmall.seckill.SeckillScriptHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 关单 & 库存回补
 * <p>
 * 单独抽成一个 Service 而不是放在 OrderService 里，有两个原因：
 * 1. 定时任务、用户主动取消、商家拒单都要用它，抽出来避免重复；
 * 2. Spring 的 @Transactional 基于代理，同类内部方法互相调用不会走代理，
 *    放成独立 Bean 才能保证事务真的生效。
 * <p>
 * 秒杀订单与普通订单的区别：除了回补 tb_goods_sku.stock，还要回补
 * tb_seckill_sku.seckill_stock、Redis 秒杀库存/用户已购数量/售罄标记，
 * 否则未支付的秒杀名额会被永久占用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCancelService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final GoodsSkuMapper goodsSkuMapper;
    private final GoodsMapper goodsMapper;
    private final StockLogMapper stockLogMapper;
    private final SeckillSkuMapper seckillSkuMapper;
    private final SeckillUserCountMapper seckillUserCountMapper;
    private final StringRedisTemplate redis;

    /**
     * 取消「待支付」订单并回补库存
     * <p>
     * 用状态机 CAS 更新：只有真正把状态从「待支付」改成「已取消」的那一次调用才会回补库存，
     * 因此用户狂点取消、超时任务同时扫描，都不会重复回补。
     *
     * @return true 表示本次真的取消了，false 表示订单已经不是待支付状态
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelIfUnpaid(String orderNo, String reason) {
        Orders order = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(MessageConstant.ORDER_NOT_FOUND);
        }

        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Orders>()
                .set(Orders::getStatus, OrderStatus.CANCELLED.getCode())
                .set(Orders::getCancelTime, LocalDateTime.now())
                .set(Orders::getCancelReason, reason)
                .set(Orders::getUpdateTime, LocalDateTime.now())
                .eq(Orders::getId, order.getId())
                .eq(Orders::getStatus, OrderStatus.PENDING_PAYMENT.getCode()));

        if (rows == 0) {
            log.info("订单 {} 不是待支付状态，忽略关单", orderNo);
            return false;
        }

        // 回补库存（幂等：靠 tb_stock_log.biz_key 唯一索引去重）
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId()));
        Long activityId = order.getActivityId();
        for (OrderItem item : items) {
            rollbackStock(item.getSkuId(), item.getQuantity(),
                    orderNo + ":cancel:" + item.getSkuId(), StockLog.TYPE_ROLLBACK);

            // 下单时销量是 +quantity，取消后要减回去，避免取消订单虚增销量
            goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                    .setSql("sales = GREATEST(sales - " + item.getQuantity() + ", 0)")
                    .eq(Goods::getId, item.getGoodsId()));

            // 秒杀订单：真实库存之外，还要回收秒杀库存和限购名额
            if (activityId != null) {
                rollbackSeckillStock(orderNo, activityId, item.getSkuId(),
                        order.getUserId(), item.getQuantity());
            }
        }

        // 移除延迟队列中的关单任务
        redis.opsForZSet().remove(RedisConstants.ORDER_PAY_TIMEOUT_KEY, orderNo);
        log.info("订单 {} 已取消，原因：{}，回补 {} 个 SKU", orderNo, reason, items.size());
        return true;
    }

    /**
     * 回补普通 SKU 库存：先写流水（唯一键冲突 = 已经回补过，直接返回），再改库存
     */
    private void rollbackStock(Long skuId, int quantity, String bizKey, int type) {
        try {
            StockLog stockLog = new StockLog();
            stockLog.setBizKey(bizKey);
            stockLog.setSkuId(skuId);
            stockLog.setChangeQty(quantity);
            stockLog.setType(type);
            stockLog.setCreateTime(LocalDateTime.now());
            stockLogMapper.insert(stockLog);
        } catch (DuplicateKeyException e) {
            log.debug("库存已回补过，跳过: {}", bizKey);
            return;
        }
        goodsSkuMapper.update(null, new LambdaUpdateWrapper<GoodsSku>()
                .setSql("stock = stock + " + quantity)
                .eq(GoodsSku::getId, skuId));
    }

    /**
     * 回补秒杀库存：DB 的 seckill_stock / 用户购买计数 + Redis 的库存 / 用户 Hash / 售罄标记
     * <p>
     * stock_log 用 orderNo + skuId 做幂等键；Redis 回补用独立的 guard key 做幂等，
     * 这样即使事务提交失败后重试，也不会重复增加库存。
     */
    private void rollbackSeckillStock(String orderNo, Long activityId, Long skuId,
                                      Long userId, int quantity) {
        String bizKey = orderNo + ":cancel:seckill:" + skuId;
        try {
            StockLog stockLog = new StockLog();
            stockLog.setBizKey(bizKey);
            stockLog.setSkuId(skuId);
            stockLog.setActivityId(activityId);
            stockLog.setChangeQty(quantity);
            stockLog.setType(StockLog.TYPE_ROLLBACK);
            stockLog.setCreateTime(LocalDateTime.now());
            stockLogMapper.insert(stockLog);
        } catch (DuplicateKeyException e) {
            log.debug("秒杀库存已回补过，跳过: {}", bizKey);
            return;
        }

        seckillSkuMapper.update(null, new LambdaUpdateWrapper<SeckillSku>()
                .setSql("seckill_stock = seckill_stock + " + quantity)
                .eq(SeckillSku::getActivityId, activityId)
                .eq(SeckillSku::getSkuId, skuId));

        // 归还用户已购次数，允许用户重新购买
        seckillUserCountMapper.update(null, new LambdaUpdateWrapper<SeckillUserCount>()
                .setSql("bought_count = bought_count - " + quantity)
                .set(SeckillUserCount::getUpdateTime, LocalDateTime.now())
                .eq(SeckillUserCount::getActivityId, activityId)
                .eq(SeckillUserCount::getSkuId, skuId)
                .eq(SeckillUserCount::getUserId, userId)
                .ge(SeckillUserCount::getBoughtCount, quantity));

        rollbackRedisSeckillStock(orderNo, activityId, skuId, userId, quantity);
    }

    private void rollbackRedisSeckillStock(String orderNo, Long activityId, Long skuId,
                                           Long userId, int quantity) {
        String guardKey = RedisConstants.SECKILL_RETRY_KEY + "cancel:" + orderNo + ":" + skuId;
        Boolean first = redis.opsForValue().setIfAbsent(guardKey, "1", Duration.ofHours(1));
        if (!Boolean.TRUE.equals(first)) {
            return;
        }

        try {
            redis.execute(SeckillScriptHolder.ROLLBACK_SCRIPT,
                    List.of(RedisConstants.seckillStockKey(activityId, skuId),
                            RedisConstants.seckillUserKey(activityId, skuId),
                            RedisConstants.seckillSoldoutKey(activityId, skuId)),
                    String.valueOf(userId),
                    String.valueOf(quantity));
            log.info("订单 {} 已回补 Redis 秒杀库存 skuId={}", orderNo, skuId);
        } catch (Exception e) {
            // 回补失败时释放 guard，让后续重试还能再执行
            redis.delete(guardKey);
            throw e;
        }
    }
}
