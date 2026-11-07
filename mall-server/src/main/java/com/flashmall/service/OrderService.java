package com.flashmall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashmall.annotation.Idempotent;
import com.flashmall.common.constant.MessageConstant;
import com.flashmall.common.constant.RedisConstants;
import com.flashmall.common.enums.OrderStatus;
import com.flashmall.common.enums.PayStatus;
import com.flashmall.common.exception.BizException;
import com.flashmall.common.result.PageResult;
import com.flashmall.common.result.Result;
import com.flashmall.common.utils.UserHolder;
import com.flashmall.utils.RedisIdWorker;
import com.flashmall.mapper.CartMapper;
import com.flashmall.mapper.GoodsMapper;
import com.flashmall.mapper.GoodsSkuMapper;
import com.flashmall.mapper.OrderItemMapper;
import com.flashmall.mapper.OrderMapper;
import com.flashmall.mapper.PaymentMapper;
import com.flashmall.mapper.StockLogMapper;
import com.flashmall.pojo.dto.OrderSubmitDTO;
import com.flashmall.pojo.entity.Cart;
import com.flashmall.pojo.entity.Goods;
import com.flashmall.pojo.entity.GoodsSku;
import com.flashmall.pojo.entity.OrderItem;
import com.flashmall.pojo.entity.Orders;
import com.flashmall.pojo.entity.Payment;
import com.flashmall.pojo.entity.StockLog;
import com.flashmall.pojo.vo.OrderSubmitVO;
import com.flashmall.pojo.vo.OrderVO;
import com.flashmall.websocket.WebSocketServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 订单
 * <p>
 * 正确性由四道防线保证：
 * 1. 下单走事务，库存、订单、流水要么一起成功要么一起失败；
 * 2. 库存扣减用条件更新（WHERE stock >= n），影响行数为 0 即库存不足；
 * 3. 状态流转用 CAS（WHERE status = 旧值），并发下只有一个请求能生效；
 * 4. 支付回调靠 tb_payment 唯一索引去重，重复回调原样返回成功。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartMapper cartMapper;
    private final GoodsMapper goodsMapper;
    private final GoodsSkuMapper goodsSkuMapper;
    private final PaymentMapper paymentMapper;
    private final StockLogMapper stockLogMapper;
    private final StringRedisTemplate redis;
    private final RedisIdWorker idWorker;
    private final OrderCancelService orderCancelService;
    private final WebSocketServer webSocketServer;

    @Value("${flash-mall.order.pay-timeout-minutes:15}")
    private long payTimeoutMinutes;

    /* ==================== 下单 ==================== */

    /**
     * 提交订单
     * <p>
     * @Idempotent：按用户维度加 5 秒幂等窗口，挡住"用户连点提交按钮"。
     * 不能只用收货手机号：不同手机号可以绕过，不同用户使用同一手机号又会互相误伤。
     * <p>
     * 下单时重新读取 SKU 和商品：校验上下架状态，并按当前价格计算金额，避免购物车
     * 里的价格快照过期后被用来成交。
     */
    @Idempotent(timeout = 5)
    @Transactional(rollbackFor = Exception.class)
    public OrderSubmitVO submit(OrderSubmitDTO dto) {
        Long userId = UserHolder.requireUserId();

        List<Cart> cartItems = cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId));
        if (cartItems == null || cartItems.isEmpty()) {
            throw new BizException(MessageConstant.CART_EMPTY);
        }

        Map<Long, GoodsSku> skuMap = new HashMap<>(cartItems.size());
        Map<Long, Goods> goodsMap = new HashMap<>(cartItems.size());
        BigDecimal total = BigDecimal.ZERO;
        for (Cart cart : cartItems) {
            GoodsSku sku = goodsSkuMapper.selectById(cart.getSkuId());
            if (sku == null) {
                throw new BizException(MessageConstant.GOODS_NOT_FOUND);
            }
            if (sku.getStatus() == null || sku.getStatus() != 1) {
                throw new BizException(MessageConstant.GOODS_OFF_SHELF);
            }
            Goods goods = goodsMapper.selectById(sku.getGoodsId());
            if (goods == null || goods.getStatus() == null || goods.getStatus() != 1) {
                throw new BizException(MessageConstant.GOODS_OFF_SHELF);
            }
            if (sku.getPrice() == null) {
                throw new BizException("商品价格异常，请稍后重试");
            }
            if (cart.getPrice() == null || cart.getPrice().compareTo(sku.getPrice()) != 0) {
                log.info("购物车价格已变化，按最新价格下单 skuId={}, cartPrice={}, currentPrice={}",
                        sku.getId(), cart.getPrice(), sku.getPrice());
            }
            skuMap.put(sku.getId(), sku);
            goodsMap.put(goods.getId(), goods);
            total = total.add(sku.getPrice().multiply(BigDecimal.valueOf(cart.getQuantity())));
        }

        long orderId = idWorker.nextId("order");
        String orderNo = String.valueOf(orderId);
        LocalDateTime now = LocalDateTime.now();

        Orders order = Orders.builder()
                .id(orderId)
                .orderNo(orderNo)
                .userId(userId)
                .merchantId(1L)
                .totalAmount(total)
                .payAmount(total)
                .status(OrderStatus.PENDING_PAYMENT.getCode())
                .payStatus(PayStatus.UNPAID.getCode())
                .receiverName(dto.getReceiverName())
                .receiverPhone(dto.getReceiverPhone())
                .receiverAddr(dto.getReceiverAddr())
                .remark(dto.getRemark())
                .createTime(now)
                .updateTime(now)
                .build();
        orderMapper.insert(order);

        for (Cart cart : cartItems) {
            GoodsSku sku = skuMap.get(cart.getSkuId());
            Goods goods = goodsMap.get(sku.getGoodsId());

            // 扣库存（条件更新，失败抛异常让整个事务回滚）
            deductStock(sku.getId(), cart.getQuantity(),
                    orderNo + ":deduct:" + sku.getId());

            OrderItem item = new OrderItem();
            item.setOrderId(orderId);
            item.setSkuId(sku.getId());
            item.setGoodsId(goods.getId());
            item.setGoodsName(goods.getName());
            item.setSpecJson(sku.getSpecJson());
            item.setCoverUrl(goods.getCoverUrl());
            item.setPrice(sku.getPrice());
            item.setQuantity(cart.getQuantity());
            item.setAmount(sku.getPrice().multiply(BigDecimal.valueOf(cart.getQuantity())));
            orderItemMapper.insert(item);

            // 累计销量（下单即计入，真实业务一般支付后才计）
            goodsMapper.update(null, new LambdaUpdateWrapper<Goods>()
                    .setSql("sales = sales + " + cart.getQuantity())
                    .eq(Goods::getId, goods.getId()));
        }

        // 清空购物车
        cartMapper.delete(new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, userId));

        // 投递延迟关单任务：score 为到期时间戳
        redis.opsForZSet().add(RedisConstants.ORDER_PAY_TIMEOUT_KEY, orderNo,
                System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(payTimeoutMinutes));

        log.info("用户 {} 下单成功，orderNo={}, 金额={}", userId, orderNo, total);
        return OrderSubmitVO.builder()
                .orderId(orderId)
                .orderNo(orderNo)
                .payAmount(total)
                .createTime(now)
                .build();
    }

    /* ==================== 支付 ==================== */

    /**
     * 模拟预支付：真实项目这里调用微信支付 V3 下单接口拿 prepay_id
     */
    public Map<String, Object> mockPrepay(String orderNo) {
        Orders order = getByOrderNo(orderNo);
        Long userId = UserHolder.getUserId();
        if (userId != null && !UserHolder.isAdmin() && !userId.equals(order.getUserId())) {
            throw new BizException(Result.FORBIDDEN, "无权操作该订单");
        }
        if (order.getStatus() == null || order.getStatus() != OrderStatus.PENDING_PAYMENT.getCode()) {
            throw new BizException("订单已支付或已关闭");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", orderNo);
        result.put("amount", order.getPayAmount());
        result.put("channel", "WECHAT_MOCK");
        result.put("prepayId", "mock_prepay_" + orderNo);
        result.put("next", "调用 POST /api/pay/notify 模拟微信支付成功回调");
        return result;
    }

    /**
     * 支付成功处理（由支付回调触发）
     * <p>
     * 幂等设计：已支付/已退款订单的重复回调直接返回成功；支付流水以
     * (order_no, channel) 唯一索引兜底。订单已被取消时抛业务异常，
     * 让本次 payment 写入一起回滚，避免出现「钱收了、单关了」的脏数据。
     */
    @Transactional(rollbackFor = Exception.class)
    public void paySuccess(String orderNo, String tradeNo) {
        Orders order = getByOrderNo(orderNo);

        // 已支付/已退款的订单：重复回调直接返回成功（第三方要求幂等）
        if (order.getStatus() != null && order.getStatus() != OrderStatus.PENDING_PAYMENT.getCode()
                && order.getPayStatus() != null && order.getPayStatus() != PayStatus.UNPAID.getCode()) {
            if (order.getStatus() == OrderStatus.CANCELLED.getCode()) {
                log.error("订单 {} 已取消但支付状态为已支付，需要人工退款", orderNo);
            }
            log.info("支付回调重复，直接返回成功: {}", orderNo);
            return;
        }
        // 已取消且未支付等状态：不能把支付流水记成功，否则会出现「钱收了、单关了」的脏数据
        if (order.getStatus() == null || order.getStatus() != OrderStatus.PENDING_PAYMENT.getCode()) {
            log.warn("订单 {} 当前状态 {}，支付回调无法完成", orderNo, order.getStatus());
            throw new BizException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Payment payment = new Payment();
        payment.setOrderNo(orderNo);
        payment.setTradeNo(tradeNo);
        payment.setChannel(Payment.CHANNEL_WECHAT);
        payment.setAmount(order.getPayAmount());
        payment.setStatus(Payment.STATUS_SUCCESS);
        payment.setCallbackTime(LocalDateTime.now());
        try {
            paymentMapper.insert(payment);
        } catch (DuplicateKeyException e) {
            log.info("支付回调重复，直接返回成功: {}", orderNo);
            return;
        }

        // 状态机 CAS：只有「待支付」才能变成「待接单」
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Orders>()
                .set(Orders::getStatus, OrderStatus.TO_BE_CONFIRMED.getCode())
                .set(Orders::getPayStatus, PayStatus.PAID.getCode())
                .set(Orders::getPayTime, LocalDateTime.now())
                .set(Orders::getUpdateTime, LocalDateTime.now())
                .eq(Orders::getId, order.getId())
                .eq(Orders::getStatus, OrderStatus.PENDING_PAYMENT.getCode()));
        if (rows == 0) {
            // 并发下状态被其他请求改变（比如刚好被取消）：抛异常让 payment 写入一起回滚
            log.warn("订单 {} 状态已变更，本次支付回调不生效", orderNo);
            throw new BizException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 关单任务已无意义
        redis.opsForZSet().remove(RedisConstants.ORDER_PAY_TIMEOUT_KEY, orderNo);

        // 商家端来单提醒
        notifyMerchant(order);
        log.info("订单 {} 支付成功，已通知商家", orderNo);
    }

    private void notifyMerchant(Orders order) {
        String message = "{\"type\":1,\"orderNo\":\"" + order.getOrderNo()
                + "\",\"content\":\"您有新的订单，请及时处理\"}";
        try {
            webSocketServer.sendToAll(message);
        } catch (Exception e) {
            // 推送失败不影响主流程
            log.warn("WebSocket 来单提醒推送失败: {}", order.getOrderNo(), e);
        }
    }

    /* ==================== 查询 ==================== */

    public PageResult<Orders> myPage(Integer status, long pageNo, long pageSize) {
        Long userId = UserHolder.requireUserId();
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<Orders>()
                .eq(Orders::getUserId, userId)
                .eq(status != null, Orders::getStatus, status)
                .orderByDesc(Orders::getCreateTime);
        Page<Orders> page = orderMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    public PageResult<Orders> adminPage(Integer status, long pageNo, long pageSize) {
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<Orders>()
                .eq(status != null, Orders::getStatus, status)
                .orderByDesc(Orders::getCreateTime);
        Page<Orders> page = orderMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    public OrderVO detail(String orderNo) {
        Orders order = getByOrderNo(orderNo);
        Long userId = UserHolder.getUserId();
        if (userId != null && !UserHolder.isAdmin() && !userId.equals(order.getUserId())) {
            throw new BizException(Result.FORBIDDEN, "无权查看该订单");
        }
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId()));
        OrderVO vo = new OrderVO();
        vo.setOrder(order);
        vo.setItems(items);
        OrderStatus status = OrderStatus.of(order.getStatus());
        vo.setStatusDesc(status == null ? "" : status.getDesc());
        return vo;
    }

    /* ==================== 取消 & 状态流转 ==================== */

    public void cancelByUser(String orderNo, String reason) {
        Long userId = UserHolder.requireUserId();
        Orders order = getByOrderNo(orderNo);
        if (!userId.equals(order.getUserId())) {
            throw new BizException(Result.FORBIDDEN, "无权操作该订单");
        }
        boolean cancelled = orderCancelService.cancelIfUnpaid(orderNo, reason);
        if (!cancelled) {
            throw new BizException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 通用状态流转（商家接单 / 派送 / 完成）
     */
    @Transactional(rollbackFor = Exception.class)
    public void transit(String orderNo, OrderStatus from, OrderStatus to) {
        if (!OrderStatus.canTransit(from.getCode(), to.getCode())) {
            throw new BizException("非法的状态流转: " + from + " -> " + to);
        }
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Orders>()
                .set(Orders::getStatus, to.getCode())
                .set(Orders::getUpdateTime, LocalDateTime.now())
                .eq(Orders::getOrderNo, orderNo)
                .eq(Orders::getStatus, from.getCode()));
        if (rows == 0) {
            throw new BizException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /* ==================== 超时关单 ==================== */

    /**
     * 扫描超时未支付订单
     * <p>
     * 双保险：Redis ZSet 提供秒级精度，数据库扫表兜住「Redis 丢数据」的极端情况。
     */
    public int scanTimeoutOrders() {
        int count = 0;
        long now = System.currentTimeMillis();

        Set<String> expired = redis.opsForZSet()
                .rangeByScore(RedisConstants.ORDER_PAY_TIMEOUT_KEY, 0, now, 0, 200);
        if (expired != null) {
            for (String orderNo : expired) {
                try {
                    if (orderCancelService.cancelIfUnpaid(orderNo, "超时未支付")) {
                        count++;
                    }
                } catch (Exception e) {
                    log.error("关单失败: {}", orderNo, e);
                } finally {
                    redis.opsForZSet().remove(RedisConstants.ORDER_PAY_TIMEOUT_KEY, orderNo);
                }
            }
        }

        // 扫表兜底
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(payTimeoutMinutes);
        List<Orders> timeoutOrders = orderMapper.selectList(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getStatus, OrderStatus.PENDING_PAYMENT.getCode())
                .lt(Orders::getCreateTime, deadline)
                .last("limit 200"));
        for (Orders order : timeoutOrders) {
            try {
                if (orderCancelService.cancelIfUnpaid(order.getOrderNo(), "超时未支付")) {
                    count++;
                }
            } catch (Exception e) {
                log.error("扫表关单失败: {}", order.getOrderNo(), e);
            }
        }
        return count;
    }

    /* ==================== 内部工具 ==================== */

    private Orders getByOrderNo(String orderNo) {
        Orders order = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(MessageConstant.ORDER_NOT_FOUND);
        }
        return order;
    }

    /**
     * 扣库存：先写幂等流水，再做条件更新
     * <p>
     * WHERE stock >= quantity 是防超卖的关键——把「判断」和「扣减」合成一条 SQL，
     * 避免"先查再改"中间被别的请求插队。
     */
    private void deductStock(Long skuId, int quantity, String bizKey) {
        try {
            StockLog stockLog = new StockLog();
            stockLog.setBizKey(bizKey);
            stockLog.setSkuId(skuId);
            stockLog.setChangeQty(-quantity);
            stockLog.setType(StockLog.TYPE_DEDUCT);
            stockLog.setCreateTime(LocalDateTime.now());
            stockLogMapper.insert(stockLog);
        } catch (DuplicateKeyException e) {
            log.info("库存已扣减过，跳过: {}", bizKey);
            return;
        }

        GoodsSku sku = goodsSkuMapper.selectById(skuId);
        if (sku == null) {
            throw new BizException(MessageConstant.GOODS_NOT_FOUND);
        }
        int rows = goodsSkuMapper.update(null, new LambdaUpdateWrapper<GoodsSku>()
                .setSql("stock = stock - " + quantity)
                .eq(GoodsSku::getId, skuId)
                .ge(GoodsSku::getStock, quantity));
        if (rows == 0) {
            throw new BizException(MessageConstant.STOCK_NOT_ENOUGH);
        }
    }
}
