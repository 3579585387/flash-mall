package com.flashmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashmall.pojo.entity.SeckillSku;
import org.apache.ibatis.annotations.Mapper;

/**
 * 秒杀商品
 */
@Mapper
public interface SeckillSkuMapper extends BaseMapper<SeckillSku> {
}
