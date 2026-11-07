package com.flashmall.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水
 * <p>
 * (order_no, channel) 唯一索引是回调幂等的基石：微信重复回调时
 * 第二次插入直接抛唯一键冲突，业务侧捕获后原样返回成功即可。
 */
@Data
@TableName("tb_payment")
public class Payment implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int CHANNEL_WECHAT = 1;
    public static final int STATUS_INIT = 0;
    public static final int STATUS_SUCCESS = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    /** 第三方交易号 */
    private String tradeNo;

    private Integer channel;

    private BigDecimal amount;

    private Integer status;

    private LocalDateTime callbackTime;
}
