package com.flashmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashmall.pojo.entity.Goods;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品 SPU
 */
@Mapper
public interface GoodsMapper extends BaseMapper<Goods> {
}
