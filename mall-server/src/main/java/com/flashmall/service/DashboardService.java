package com.flashmall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashmall.common.enums.OrderStatus;
import com.flashmall.common.enums.PayStatus;
import com.flashmall.mapper.GoodsMapper;
import com.flashmall.mapper.OrderMapper;
import com.flashmall.pojo.entity.Goods;
import com.flashmall.pojo.entity.Orders;
import com.flashmall.pojo.vo.DashboardVO;
import com.flashmall.pojo.vo.GoodsSalesVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 运营看板
 * <p>
 * 演示项目直接聚合查询；数据量大了之后应该：
 * 1. 定时任务预计算并写入统计表（T+1 报表），或
 * 2. 走 ClickHouse / ES 这类 OLAP 存储，避免统计查询拖慢业务库。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final OrderMapper orderMapper;
    private final GoodsMapper goodsMapper;

    public DashboardVO dashboard() {
        DashboardVO vo = new DashboardVO();

        vo.setTotalOrders(orderMapper.selectCount(null));
        vo.setTodayOrders(orderMapper.selectCount(new LambdaQueryWrapper<Orders>()
                .ge(Orders::getCreateTime, LocalDate.now().atStartOfDay())));
        vo.setPendingOrders(orderMapper.selectCount(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getStatus, OrderStatus.TO_BE_CONFIRMED.getCode())));
        vo.setSeckillOrders(orderMapper.selectCount(new LambdaQueryWrapper<Orders>()
                .isNotNull(Orders::getActivityId)));

        // 成交额：只统计已支付订单
        List<Orders> paidOrders = orderMapper.selectList(new LambdaQueryWrapper<Orders>()
                .select(Orders::getPayAmount)
                .eq(Orders::getPayStatus, PayStatus.PAID.getCode()));
        BigDecimal turnover = paidOrders.stream()
                .map(Orders::getPayAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setTotalTurnover(turnover);

        // 销量 Top 5
        List<Goods> topGoods = goodsMapper.selectList(new LambdaQueryWrapper<Goods>()
                .select(Goods::getName, Goods::getSales)
                .orderByDesc(Goods::getSales)
                .last("limit 5"));
        List<GoodsSalesVO> salesList = new ArrayList<>(topGoods.size());
        for (Goods goods : topGoods) {
            salesList.add(new GoodsSalesVO(goods.getName(),
                    goods.getSales() == null ? 0L : goods.getSales().longValue()));
        }
        vo.setTopGoods(salesList);

        return vo;
    }
}
