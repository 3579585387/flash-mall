package com.flashmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashmall.pojo.entity.SeckillOrderRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 秒杀下单去重记录
 */
@Mapper
public interface SeckillOrderRecordMapper extends BaseMapper<SeckillOrderRecord> {
}
