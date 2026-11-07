package com.flashmall.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车
 * <p>
 * 名称、规格、价格都是加入购物车那一刻的快照，
 * 真正下单时会再次校验价格与库存。
 */
@Data
@TableName("tb_cart")
public class Cart implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long skuId;

    private Long goodsId;

    private String goodsName;

    private String specJson;

    private String coverUrl;

    private BigDecimal price;

    private Integer quantity;

    private LocalDateTime createTime;
}
