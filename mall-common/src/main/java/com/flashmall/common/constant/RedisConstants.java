package com.flashmall.common.constant;

/**
 * Redis Key 规范
 * <p>
 * 约定：业务前缀 + 冒号分层。所有 key 都必须设置过期时间，
 * 避免"活动结束很久了 key 还挂在 Redis 里"造成内存泄漏。
 */
public class RedisConstants {

    private RedisConstants() {
    }

    /* ================== 登录 ================== */

    /** 登录验证码 */
    public static final String LOGIN_CODE_KEY = "login:code:";
    /** 验证码有效期（分钟） */
    public static final long LOGIN_CODE_TTL_MINUTES = 2L;

    /** 登录令牌（Hash：id / nickName / type） */
    public static final String LOGIN_TOKEN_KEY = "login:token:";
    /** 令牌有效期（分钟） */
    public static final long LOGIN_TOKEN_TTL_MINUTES = 30L;
    /** 剩余有效期低于该值则续期（滑动续期） */
    public static final long LOGIN_TOKEN_REFRESH_MINUTES = 10L;

    /** 用户类型：C 端 */
    public static final int USER_TYPE_USER = 1;
    /** 用户类型：B 端商家 */
    public static final int USER_TYPE_ADMIN = 2;

    /* ================== 商品缓存 ================== */

    /** 商品详情缓存 key 前缀：goods:detail:{skuId} */
    public static final String GOODS_DETAIL_KEY = "goods:detail:";
    /** 商品详情逻辑过期时间（分钟） */
    public static final long GOODS_DETAIL_TTL_MINUTES = 30L;
    /** 空值缓存 TTL（分钟）：防穿透用，不能太长否则数据新增后长时间看不到 */
    public static final long CACHE_NULL_TTL_MINUTES = 2L;

    /** 缓存重建互斥锁前缀 */
    public static final String LOCK_REBUILD_KEY = "lock:rebuild:";
    /** 下单幂等锁前缀（预留，需要分布式锁时可使用） */
    public static final String LOCK_ORDER_KEY = "lock:order:";

    /* ================== 秒杀 ================== */

    /** 秒杀剩余库存：seckill:stock:{activityId}:{skuId} */
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    /** 秒杀已购用户及数量（Hash）：seckill:user:{activityId}:{skuId} */
    public static final String SECKILL_USER_KEY = "seckill:user:";
    /** 售罄标记：seckill:soldout:{activityId}:{skuId} */
    public static final String SECKILL_SOLDOUT_KEY = "seckill:soldout:";
    /** 每人限购数：seckill:limit:{activityId}:{skuId} */
    public static final String SECKILL_LIMIT_KEY = "seckill:limit:";
    /** 活动开始时间戳：seckill:begin:{activityId} */
    public static final String SECKILL_BEGIN_KEY = "seckill:begin:";
    /** 活动结束时间戳：seckill:end:{activityId} */
    public static final String SECKILL_END_KEY = "seckill:end:";
    /** 秒杀结果：seckill:result:{orderNo} 取值 PROCESSING / SUCCESS / FAIL */
    public static final String SECKILL_RESULT_KEY = "seckill:result:";
    /** 秒杀结果保留时长（分钟） */
    public static final long SECKILL_RESULT_TTL_MINUTES = 30L;

    public static final String SECKILL_RESULT_PROCESSING = "PROCESSING";
    public static final String SECKILL_RESULT_SUCCESS = "SUCCESS";
    public static final String SECKILL_RESULT_FAIL = "FAIL";

    /** 秒杀下单消息流 */
    public static final String STREAM_SECKILL = "stream.seckill.order";
    /** 消费组 */
    public static final String STREAM_SECKILL_GROUP = "seckill-group";
    /** 死信队列 */
    public static final String STREAM_SECKILL_DLQ = "stream.seckill.dlq";
    /** 消息重试次数计数前缀 */
    public static final String SECKILL_RETRY_KEY = "seckill:retry:";

    /* ================== 订单 ================== */

    /** 待支付订单延迟队列（ZSet，score = 到期时间戳） */
    public static final String ORDER_PAY_TIMEOUT_KEY = "order:pay:timeout";
    /** 下单幂等锁前缀（用户维度，预留） */
    public static final String ORDER_SUBMIT_LOCK_KEY = "lock:order:submit:";

    /* ================== 通用 ================== */

    /** 全局 ID 序列号自增 key 前缀：icr:{业务}:{yyyy:MM:dd} */
    public static final String ID_WORKER_KEY = "icr:";
    /** 接口限流计数器前缀 */
    public static final String LIMIT_KEY = "limit:";
    /** 接口幂等前缀 */
    public static final String IDEMPOTENT_KEY = "idempotent:";

    /* ================== key 拼装工具 ================== */

    public static String seckillStockKey(Object activityId, Object skuId) {
        return SECKILL_STOCK_KEY + activityId + ":" + skuId;
    }

    public static String seckillUserKey(Object activityId, Object skuId) {
        return SECKILL_USER_KEY + activityId + ":" + skuId;
    }

    public static String seckillSoldoutKey(Object activityId, Object skuId) {
        return SECKILL_SOLDOUT_KEY + activityId + ":" + skuId;
    }

    public static String seckillLimitKey(Object activityId, Object skuId) {
        return SECKILL_LIMIT_KEY + activityId + ":" + skuId;
    }
}
