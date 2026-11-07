package com.flashmall.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息幂等表
 * <p>
 * 消费前先插入，主键/唯一键冲突说明这条消息已经处理过，直接丢弃。
 */
@Data
@TableName("tb_mq_message")
public class MqMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int STATUS_PROCESSING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAIL = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 消息唯一标识（本项目直接用 orderNo） */
    private String msgId;

    private String topic;

    private Integer status;

    private Integer retryCount;

    private LocalDateTime createTime;
}
