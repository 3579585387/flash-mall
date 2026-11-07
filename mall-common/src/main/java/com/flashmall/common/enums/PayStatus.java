package com.flashmall.common.enums;

import lombok.Getter;

/**
 * 支付状态
 */
@Getter
public enum PayStatus {

    UNPAID(0, "未支付"),
    PAID(1, "已支付"),
    REFUNDED(2, "已退款");

    private final int code;
    private final String desc;

    PayStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
