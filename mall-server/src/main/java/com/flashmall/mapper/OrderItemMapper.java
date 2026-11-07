package com.flashmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashmall.pojo.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单明细
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
