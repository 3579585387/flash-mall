package com.flashmall.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 商品销量排行项
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoodsSalesVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String goodsName;

    private Long sales;
}
