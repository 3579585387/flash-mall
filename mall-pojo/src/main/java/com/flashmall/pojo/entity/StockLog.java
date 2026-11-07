package com.flashmall.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 库存变更流水
 * <p>
 * 幂等的关键：biz_key 唯一索引。先插流水（冲突即说明处理过，直接返回），再改库存，
 * 这样消息重复投递、用户重复点取消，库存也只变更一次。
 */
@Data
@TableName("tb_stock_log")
public class StockLog implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int TYPE_DEDUCT = 1;
    public static final int TYPE_ROLLBACK = 2;
    public static final int TYPE_FAIL_ROLLBACK = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 幂等键，例如 orderNo:deduct */
    private String bizKey;

    private Long skuId;

    private Long activityId;

    /** 正数回补，负数扣减 */
    private Integer changeQty;

    private Integer type;

    private LocalDateTime createTime;
}
