package com.flashmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashmall.pojo.entity.StockLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库存流水（幂等）
 */
@Mapper
public interface StockLogMapper extends BaseMapper<StockLog> {
}
