package com.flashmall.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表
 * <p>
 * 主键由 RedisIdWorker 生成（趋势递增、不暴露业务量），
 * 所以必须是 IdType.INPUT，由应用显式赋值。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_orders")
public class Orders implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 业务订单号（展示 / 支付用） */
    private String orderNo;

    private Long userId;

    private Long merchantId;

    /** 秒杀活动 id，普通订单为 null */
    private Long activityId;

    private BigDecimal totalAmount;

    private BigDecimal payAmount;

    /** 见 com.flashmall.common.enums.OrderStatus */
    private Integer status;

    /** 见 com.flashmall.common.enums.PayStatus */
    private Integer payStatus;

    private Integer payMethod;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddr;

    private String remark;

    private LocalDateTime payTime;

    private LocalDateTime cancelTime;

    private String cancelReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
