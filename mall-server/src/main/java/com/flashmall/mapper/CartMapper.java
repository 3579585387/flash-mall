package com.flashmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashmall.pojo.entity.Cart;
import org.apache.ibatis.annotations.Mapper;

/**
 * 购物车
 */
@Mapper
public interface CartMapper extends BaseMapper<Cart> {
}
