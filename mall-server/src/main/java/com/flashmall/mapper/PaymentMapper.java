package com.flashmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashmall.pojo.entity.Payment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付流水
 */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {
}
