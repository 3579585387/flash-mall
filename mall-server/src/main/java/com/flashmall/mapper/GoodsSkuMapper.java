package com.flashmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashmall.pojo.entity.GoodsSku;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品 SKU
 */
@Mapper
public interface GoodsSkuMapper extends BaseMapper<GoodsSku> {
}
