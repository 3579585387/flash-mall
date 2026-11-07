package com.flashmall.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品 SKU：库存与价格的最小单位
 */
@Data
@TableName("tb_goods_sku")
public class GoodsSku implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long goodsId;

    private String skuCode;

    /** 规格快照，例如 {"颜色":"曜石黑","容量":"256G"} */
    private String specJson;

    private BigDecimal price;

    /** 真实库存 */
    private Integer stock;

    /** 乐观锁版本号 */
    private Integer version;

    private Integer status;
}
