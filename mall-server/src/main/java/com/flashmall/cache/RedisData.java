package com.flashmall.cache;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 逻辑过期包装：缓存里不设置真实 TTL，而是在 value 里带一个过期时间
 * <p>
 * 好处：key 永远不会"消失"，请求不会因为 key 过期而阻塞等数据库；
 * 代价：会短暂返回旧数据，所以只适合商品详情这类可容忍短暂不一致的数据。
 */
@Data
public class RedisData implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 真实数据 */
    private Object data;

    /** 逻辑过期时间 */
    private LocalDateTime expireTime;
}
