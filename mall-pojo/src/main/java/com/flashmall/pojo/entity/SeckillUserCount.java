package com.flashmall.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 秒杀用户购买计数
 * <p>
 * 用于支持「每人限购 N 件」：Redis 是第一道拦截，这张表是数据库侧最终兜底。
 * 唯一键 (activity_id, sku_id, user_id) 保证一个用户一个活动/SKU 只有一行，
 * bought_count 记录已购数量，消费者通过条件更新 bought_count &lt; limit 原子累加。
 */
@Data
@TableName("tb_seckill_user_count")
public class SeckillUserCount implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long skuId;

    private Long userId;

    /** 已购数量 */
    private Integer boughtCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
