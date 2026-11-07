package com.flashmall.common.constant;

/**
 * 提示信息常量：避免提示语散落在各个 Service 里
 */
public class MessageConstant {

    private MessageConstant() {
    }

    public static final String PHONE_ILLEGAL = "手机号格式不正确";
    public static final String CODE_ERROR = "验证码错误或已过期";
    public static final String LOGIN_REQUIRED = "请先登录";
    public static final String NO_PERMISSION = "无访问权限";
    public static final String ACCOUNT_ERROR = "账号或密码错误";
    public static final String ACCOUNT_LOCKED = "账号已被禁用";

    public static final String GOODS_NOT_FOUND = "商品不存在";
    public static final String GOODS_OFF_SHELF = "商品已下架";
    public static final String STOCK_NOT_ENOUGH = "库存不足";

    public static final String CART_EMPTY = "购物车为空，请先挑选商品";
    public static final String ORDER_NOT_FOUND = "订单不存在";
    public static final String ORDER_STATUS_ERROR = "订单状态已变更，请刷新后重试";
    public static final String PAY_REPEAT = "请勿重复提交";

    public static final String SECKILL_SOLD_OUT = "已售罄";
    public static final String SECKILL_LIMIT = "超出限购数量";
    public static final String SECKILL_NOT_START = "活动尚未开始";
    public static final String SECKILL_ENDED = "活动已结束";
    public static final String SECKILL_BUSY = "活动太火爆，请稍后重试";

    public static final String REPEAT_SUBMIT = "请勿重复提交";
    public static final String TOO_MANY_REQUESTS = "请求过于频繁，请稍后再试";
}
