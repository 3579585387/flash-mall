package com.flashmall.common.enums;

import lombok.Getter;

/**
 * 秒杀活动状态
 */
@Getter
public enum ActivityStatus {

    NOT_STARTED(0, "未开始"),
    ONGOING(1, "进行中"),
    ENDED(2, "已结束"),
    PREHEAT_FAILED(3, "预热失败");

    private final int code;
    private final String desc;

    ActivityStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
