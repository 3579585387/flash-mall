package com.flashmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashmall.pojo.entity.Orders;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单
 */
@Mapper
public interface OrderMapper extends BaseMapper<Orders> {
}
