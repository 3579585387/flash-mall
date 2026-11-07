package com.flashmall.seckill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.flashmall.common.constant.MessageConstant;
import com.flashmall.common.constant.RedisConstants;
import com.flashmall.common.enums.OrderStatus;
import com.flashmall.common.enums.PayStatus;
import com.flashmall.common.exception.BizException;
import com.flashmall.mapper.GoodsMapper;
import com.flashmall.mapper.GoodsSkuMapper;
import com.flashmall.mapper.MqMessageMapper;
import com.flashmall.mapper.OrderItemMapper;
import com.flashmall.mapper.OrderMapper;
import com.flashmall.mapper.SeckillSkuMapper;
import com.flashmall.mapper.SeckillUserCountMapper;
import com.flashmall.mapper.StockLogMapper;
import com.flashmall.pojo.entity.Goods;
import com.flashmall.pojo.entity.GoodsSku;
import com.flashmall.pojo.entity.MqMessage;
import com.flashmall.pojo.entity.OrderItem;
import com.flashmall.pojo.entity.Orders;
import com.flashmall.pojo.entity.SeckillSku;
import com.flashmall.pojo.entity.SeckillUserCount;
import com.flashmall.pojo.entity.StockLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀订单落库（消费者专用，整个过程在一个本地事务里）
 * <p>
 * 顺序很重要：
 * 1. 先插消息幂等表 → 重复投递会在这里被唯一键挡掉；
 * 2. 再累加用户购买次数 → Redis 数据丢失时，靠 DB 条件更新仍然执行限购；
 * 3. 然后扣两次库存（真实库存 + 秒杀库存），都是条件更新；
 * 4. 最后写订单、明细、流水。
 * 任何一步失败都会整体回滚，并由消费者回补 Redis 库存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillOrderCreator {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final GoodsMapper goodsMapper;
    private final GoodsSkuMapper goodsSkuMapper;
    private final SeckillSkuMapper seckillSkuMapper;
    private final SeckillUserCountMapper seckillUserCountMapper;
    private final MqMessageMapper mqMessageMapper;
    private final StockLogMapper stockLogMapper;
    private final StringRedisTemplate redis;

    @Value("${flash-mall.order.pay-timeout-minutes:15}")
    private long payTimeoutMinutes;

    @Transactional(rollbackFor = Exception.class)
    public Orders create(String orderNo, Long userId, Long activityId, Long skuId) {
        // 1. 消息幂等（唯一键冲突会抛 DuplicateKeyException，由消费者识别为"已处理"）
        MqMessage message = new MqMessage();
        message.setMsgId(orderNo);
        message.setTopic(RedisConstants.STREAM_SECKILL);
        message.setStatus(MqMessage.STATUS_PROCESSING);
        message.setRetryCount(0);
        message.setCreateTime(LocalDateTime.now());
        mqMessageMapper.insert(message);

        SeckillSku seckillSku = seckillSkuMapper.selectOne(new LambdaQueryWrapper<SeckillSku>()
                .eq(SeckillSku::getActivityId, activityId)
                .eq(SeckillSku::getSkuId, skuId));
        if (seckillSku == null) {
            throw new BizException("秒杀商品不存在");
        }

        GoodsSku sku = goodsSkuMapper.selectById(skuId);
        if (sku == null) {
            throw new BizException(MessageConstant.GOODS_NOT_FOUND);
        }
        Goods goods = goodsMapper.selectById(sku.getGoodsId());
        if (goods == null) {
            throw new BizException(MessageConstant.GOODS_NOT_FOUND);
        }

        // 2. 每人限购的最终兜底：按 (activity, sku, user) 原子累加购买次数
        int limit = seckillSku.getLimitPerUser() == null || seckillSku.getLimitPerUser() < 1
                ? 1 : seckillSku.getLimitPerUser();
        incrementUserBoughtCount(activityId, skuId, userId, limit);

        // 3. 扣减真实库存（条件更新，影响行数为 0 说明库存不足）
        int stockRows = goodsSkuMapper.update(null, new LambdaUpdateWrapper<GoodsSku>()
                .setSql("stock = stock - 1")
                .eq(GoodsSku::getId, skuId)
                .ge(GoodsSku::getStock, 1));
        if (stockRows == 0) {
            throw new BizException(MessageConstant.STOCK_NOT_ENOUGH);
        }

        // 4. 扣减秒杀库存（数据库侧，Redis 是它的镜像）
        int seckillRows = seckillSkuMapper.update(null, new LambdaUpdateWrapper<SeckillSku>()
                .setSql("seckill_stock = seckill_stock - 1")
                .eq(SeckillSku::getId, seckillSku.getId())
                .ge(SeckillSku::getSeckillStock, 1));
        if (seckillRows == 0) {
            throw new BizException(MessageConstant.SECKILL_SOLD_OUT);
        }

        // 5. 库存流水
        StockLog stockLog = new StockLog();
        stockLog.setBizKey(orderNo + ":seckill:deduct");
        stockLog.setSkuId(skuId);
        stockLog.setActivityId(activityId);
        stockLog.setChangeQty(-1);
        stockLog.setType(StockLog.TYPE_DEDUCT);
        stockLog.setCreateTime(LocalDateTime.now());
        stockLogMapper.insert(stockLog);

        // 6. 订单 + 明细
        LocalDateTime now = LocalDateTime.now();
        long orderId = Long.parseLong(orderNo);
        Orders order = Orders.builder()
                .id(orderId)
                .orderNo(orderNo)
                .userId(userId)
                .merchantId(goods.getMerchantId() == null ? 1L : goods.getMerchantId())
                .activityId(activityId)
                .totalAmount(seckillSku.getSeckillPrice())
                .payAmount(seckillSku.getSeckillPrice())
                .status(OrderStatus.PENDING_PAYMENT.getCode())
                .payStatus(PayStatus.UNPAID.getCode())
                .receiverName("待补充")
                .receiverPhone("待补充")
                .receiverAddr("秒杀订单，请在支付前补充收货信息")
                .createTime(now)
                .updateTime(now)
                .build();
        orderMapper.insert(order);

        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setSkuId(skuId);
        item.setGoodsId(goods.getId());
        item.setGoodsName(goods.getName());
        item.setSpecJson(sku.getSpecJson());
        item.setCoverUrl(goods.getCoverUrl());
        item.setPrice(seckillSku.getSeckillPrice());
        item.setQuantity(1);
        item.setAmount(seckillSku.getSeckillPrice());
        orderItemMapper.insert(item);

        // 7. 累计销量
        goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                .setSql("sales = sales + 1")
                .eq(Goods::getId, goods.getId()));

        // 8. 消息标记成功
        mqMessageMapper.update(null, new LambdaUpdateWrapper<MqMessage>()
                .set(MqMessage::getStatus, MqMessage.STATUS_SUCCESS)
                .eq(MqMessage::getMsgId, orderNo));

        // 9. 投递延迟关单任务
        redis.opsForZSet().add(RedisConstants.ORDER_PAY_TIMEOUT_KEY, orderNo,
                System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(payTimeoutMinutes));

        log.info("秒杀订单落库成功 orderNo={}, userId={}, skuId={}", orderNo, userId, skuId);
        return order;
    }

    /**
     * 原子累加用户购买次数，并校验是否超过限购
     * <p>
     * 先 UPDATE（bought_count &lt; limit），没有行再判断是「首次购买」还是「已达上限」：
     * 首次购买并发插入时靠唯一键兜底，插入冲突后重试一次条件更新。
     */
    private void incrementUserBoughtCount(Long activityId, Long skuId, Long userId, int limit) {
        LocalDateTime now = LocalDateTime.now();

        int rows = seckillUserCountMapper.update(null, new LambdaUpdateWrapper<SeckillUserCount>()
                .setSql("bought_count = bought_count + 1")
                .set(SeckillUserCount::getUpdateTime, now)
                .eq(SeckillUserCount::getActivityId, activityId)
                .eq(SeckillUserCount::getSkuId, skuId)
                .eq(SeckillUserCount::getUserId, userId)
                .lt(SeckillUserCount::getBoughtCount, limit));
        if (rows > 0) {
            return;
        }

        SeckillUserCount existing = seckillUserCountMapper.selectOne(new LambdaQueryWrapper<SeckillUserCount>()
                .eq(SeckillUserCount::getActivityId, activityId)
                .eq(SeckillUserCount::getSkuId, skuId)
                .eq(SeckillUserCount::getUserId, userId));
        if (existing != null) {
            // 可能是并发首次购买刚插入的行，再尝试一次条件更新；仍失败才是真的超限
            int retry = seckillUserCountMapper.update(null, new LambdaUpdateWrapper<SeckillUserCount>()
                    .setSql("bought_count = bought_count + 1")
                    .set(SeckillUserCount::getUpdateTime, now)
                    .eq(SeckillUserCount::getActivityId, activityId)
                    .eq(SeckillUserCount::getSkuId, skuId)
                    .eq(SeckillUserCount::getUserId, userId)
                    .lt(SeckillUserCount::getBoughtCount, limit));
            if (retry > 0) {
                return;
            }
            throw new BizException(MessageConstant.SECKILL_LIMIT);
        }

        SeckillUserCount count = new SeckillUserCount();
        count.setActivityId(activityId);
        count.setSkuId(skuId);
        count.setUserId(userId);
        count.setBoughtCount(1);
        count.setCreateTime(now);
        count.setUpdateTime(now);
        try {
            seckillUserCountMapper.insert(count);
        } catch (DuplicateKeyException e) {
            // 并发首次购买：另一个事务先插入了，重试一次条件更新
            int retry = seckillUserCountMapper.update(null, new LambdaUpdateWrapper<SeckillUserCount>()
                    .setSql("bought_count = bought_count + 1")
                    .set(SeckillUserCount::getUpdateTime, now)
                    .eq(SeckillUserCount::getActivityId, activityId)
                    .eq(SeckillUserCount::getSkuId, skuId)
                    .eq(SeckillUserCount::getUserId, userId)
                    .lt(SeckillUserCount::getBoughtCount, limit));
            if (retry == 0) {
                throw new BizException(MessageConstant.SECKILL_LIMIT);
            }
        }
    }
}
