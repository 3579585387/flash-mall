package com.flashmall.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单明细：必须存商品快照
 * <p>
 * 商品改名、改价、下架之后，历史订单展示的仍然应该是下单那一刻的信息，
 * 所以 goods_name / spec_json / price 都是冗余快照，不是关联查询。
 */
@Data
@TableName("tb_order_item")
public class OrderItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long skuId;

    private Long goodsId;

    private String goodsName;

    private String specJson;

    private String coverUrl;

    private BigDecimal price;

    private Integer quantity;

    private BigDecimal amount;
}
