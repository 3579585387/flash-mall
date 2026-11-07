package com.flashmall.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 运营看板
 */
@Data
public class DashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总订单数 */
    private Long totalOrders;

    /** 今日订单数 */
    private Long todayOrders;

    /** 总成交额（已支付） */
    private BigDecimal totalTurnover;

    /** 待接单数 */
    private Long pendingOrders;

    /** 秒杀订单数 */
    private Long seckillOrders;

    /** 销量 Top 商品 */
    private List<GoodsSalesVO> topGoods;
}
