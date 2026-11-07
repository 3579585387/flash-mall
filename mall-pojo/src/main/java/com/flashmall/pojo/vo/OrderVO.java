package com.flashmall.pojo.vo;

import com.flashmall.pojo.entity.OrderItem;
import com.flashmall.pojo.entity.Orders;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 订单详情（订单 + 明细）
 */
@Data
public class OrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Orders order;

    private List<OrderItem> items;

    /** 状态文案，方便前端直接展示 */
    private String statusDesc;
}
