package com.flashmall.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 秒杀结果（前端拿到 orderNo 后轮询这个接口）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderNo;

    /** PROCESSING / SUCCESS / FAIL */
    private String status;

    /** 成功时返回订单号，失败时返回原因 */
    private String msg;
}
