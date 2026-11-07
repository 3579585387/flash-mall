package com.flashmall.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 秒杀商品（活动 × SKU）
 * <p>
 * 秒杀库存独立于 SKU 真实库存：活动开始时从真实库存"划扣"一部分，
 * 这样秒杀的高频扣减不会频繁锁住商品主表的库存字段。
 */
@Data
@TableName("tb_seckill_sku")
public class SeckillSku implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long skuId;

    private Long goodsId;

    private BigDecimal seckillPrice;

    /** 秒杀库存（数据库是真相，Redis 只是它的镜像） */
    private Integer seckillStock;

    /** 每人限购数 */
    private Integer limitPerUser;

    private Integer version;
}
