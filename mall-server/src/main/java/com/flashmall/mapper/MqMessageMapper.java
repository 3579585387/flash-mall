package com.flashmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashmall.pojo.entity.MqMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息幂等
 */
@Mapper
public interface MqMessageMapper extends BaseMapper<MqMessage> {
}
