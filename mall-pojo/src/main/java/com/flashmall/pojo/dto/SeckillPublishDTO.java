package com.flashmall.pojo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 发布秒杀活动参数
 */
@Data
public class SeckillPublishDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "活动标题不能为空")
    private String title;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    @NotEmpty(message = "至少要选择一个秒杀商品")
    @Valid
    private List<Item> items;

    @Data
    public static class Item implements Serializable {

        private static final long serialVersionUID = 1L;

        @NotNull(message = "skuId 不能为空")
        private Long skuId;

        @NotNull(message = "秒杀价不能为空")
        @DecimalMin(value = "0.01", message = "秒杀价必须大于 0")
        private BigDecimal seckillPrice;

        @NotNull(message = "秒杀库存不能为空")
        @Min(value = 1, message = "秒杀库存至少为 1")
        private Integer seckillStock;

        /** 每人限购，默认 1 */
        @Min(value = 1, message = "每人限购至少为 1")
        private Integer limitPerUser;
    }
}
