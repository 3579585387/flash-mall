package com.flashmall.common.utils;

import com.flashmall.common.constant.RedisConstants;

/**
 * 当前登录用户上下文（ThreadLocal）
 * <p>
 * 注意：Tomcat 线程池会复用线程，请求结束必须在拦截器 afterCompletion 里
 * 调用 {@link #remove()}，否则下一个请求会读到上一个请求的用户。
 */
public class UserHolder {

    private static final ThreadLocal<LoginUser> TL = new ThreadLocal<>();

    private UserHolder() {
    }

    public static void set(Long id, Integer type) {
        TL.set(new LoginUser(id, type));
    }

    public static LoginUser get() {
        return TL.get();
    }

    /** 可能为 null */
    public static Long getUserId() {
        LoginUser user = TL.get();
        return user == null ? null : user.getId();
    }

    /** 是否已登录 */
    public static boolean isLogin() {
        return TL.get() != null;
    }

    /** 是否 B 端商家 */
    public static boolean isAdmin() {
        LoginUser user = TL.get();
        return user != null && RedisConstants.USER_TYPE_ADMIN == user.getType();
    }

    /** 获取当前用户 id，未登录直接抛业务异常 */
    public static Long requireUserId() {
        Long id = getUserId();
        if (id == null) {
            throw new com.flashmall.common.exception.BizException(
                    com.flashmall.common.result.Result.UNAUTHORIZED,
                    com.flashmall.common.constant.MessageConstant.LOGIN_REQUIRED);
        }
        return id;
    }

    public static void remove() {
        TL.remove();
    }
}
