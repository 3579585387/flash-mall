package com.flashmall.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 秒杀下单去重记录
 * <p>
 * Redis 的 Hash 是第一道拦截，这张表的唯一索引是最终兜底：
 * 即使 Redis 数据丢失或消费重复，一个用户也不可能对同一 SKU 生成两笔订单。
 */
@Data
@TableName("tb_seckill_order_record")
public class SeckillOrderRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long skuId;

    private Long userId;

    private String orderNo;

    private LocalDateTime createTime;
}
