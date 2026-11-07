package com.flashmall.controller;

import com.flashmall.common.result.PageResult;
import com.flashmall.common.result.Result;
import com.flashmall.pojo.entity.Category;
import com.flashmall.pojo.entity.Goods;
import com.flashmall.pojo.vo.GoodsDetailVO;
import com.flashmall.service.GoodsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端商品（游客可访问）
 */
@RestController
@RequestMapping("/api/goods")
@RequiredArgsConstructor
public class GoodsController {

    private final GoodsService goodsService;

    @GetMapping("/categories")
    public Result<List<Category>> categories() {
        return Result.ok(goodsService.listCategories());
    }

    @GetMapping("/page")
    public Result<PageResult<Goods>> page(@RequestParam(required = false) Long categoryId,
                                          @RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long size) {
        return Result.ok(goodsService.page(categoryId, page, size));
    }

    /**
     * 商品详情：走三级缓存（Caffeine → Redis → MySQL）
     */
    @GetMapping("/{skuId}")
    public Result<GoodsDetailVO> detail(@PathVariable Long skuId) {
        return Result.ok(goodsService.detail(skuId));
    }
}
