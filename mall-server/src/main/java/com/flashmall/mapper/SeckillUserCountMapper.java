package com.flashmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashmall.pojo.entity.SeckillUserCount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 秒杀用户购买计数
 */
@Mapper
public interface SeckillUserCountMapper extends BaseMapper<SeckillUserCount> {
}
