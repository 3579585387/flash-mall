package com.flashmall.common.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态机
 * <p>
 * 所有状态流转都必须先经过 {@link #canTransit(int, int)} 校验，
 * 更新时再用 SQL 条件（WHERE status = 旧值）做 CAS，
 * 保证「支付回调 / 用户取消 / 超时关单 / 商家接单」并发发生时状态不会互相覆盖。
 */
@Getter
public enum OrderStatus {

    PENDING_PAYMENT(0, "待支付"),
    TO_BE_CONFIRMED(1, "待接单"),
    CONFIRMED(2, "已接单"),
    DELIVERING(3, "派送中"),
    COMPLETED(4, "已完成"),
    CANCELLED(5, "已取消"),
    REFUNDING(6, "退款中"),
    REFUNDED(7, "已退款");

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private static final Map<Integer, OrderStatus> CACHE = new HashMap<>();

    static {
        for (OrderStatus status : values()) {
            CACHE.put(status.code, status);
        }
    }

    public static OrderStatus of(Integer code) {
        return code == null ? null : CACHE.get(code);
    }

    /** 合法流转表：key = 当前状态，value = 允许流转到的状态集合 */
    private static final Map<Integer, Set<Integer>> ALLOWED = new HashMap<>();

    static {
        allow(PENDING_PAYMENT, TO_BE_CONFIRMED, CANCELLED);
        allow(TO_BE_CONFIRMED, CONFIRMED, CANCELLED, REFUNDING);
        allow(CONFIRMED, DELIVERING, CANCELLED, REFUNDING);
        allow(DELIVERING, COMPLETED, REFUNDING);
        allow(COMPLETED, REFUNDING);
        allow(REFUNDING, REFUNDED);
    }

    private static void allow(OrderStatus from, OrderStatus... targets) {
        Set<Integer> set = ALLOWED.computeIfAbsent(from.code, k -> new HashSet<>());
        for (OrderStatus target : targets) {
            set.add(target.code);
        }
    }

    public static boolean canTransit(int from, int to) {
        Set<Integer> set = ALLOWED.get(from);
        return set != null && set.contains(to);
    }
}
