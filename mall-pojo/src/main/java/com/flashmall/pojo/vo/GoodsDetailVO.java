package com.flashmall.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品详情（会被写入 Redis 缓存，所以必须能被 Jackson 反序列化）
 */
@Data
public class GoodsDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /* ---------- 商品 ---------- */
    private Long goodsId;
    private String name;
    private String subTitle;
    private String coverUrl;
    private String detail;
    private Long categoryId;

    /* ---------- SKU ---------- */
    private Long skuId;
    private String specJson;
    private BigDecimal price;
    private Integer stock;

    /* ---------- 秒杀信息（有进行中的活动才填） ---------- */
    private Long seckillActivityId;
    private BigDecimal seckillPrice;
    private Integer seckillStock;
    private Integer limitPerUser;
}
