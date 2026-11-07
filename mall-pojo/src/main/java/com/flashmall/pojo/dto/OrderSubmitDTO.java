package com.flashmall.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 提交订单参数
 * <p>
 * 演示项目把收货信息直接放在 DTO 里；真实系统应该是 addressBookId + 地址快照。
 */
@Data
public class OrderSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "收货人不能为空")
    private String receiverName;

    @NotBlank(message = "手机号不能为空")
    private String receiverPhone;

    @NotBlank(message = "收货地址不能为空")
    private String receiverAddr;

    private String remark;
}
