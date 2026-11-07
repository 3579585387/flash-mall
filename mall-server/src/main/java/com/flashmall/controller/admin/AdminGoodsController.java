package com.flashmall.controller.admin;

import com.flashmall.common.result.PageResult;
import com.flashmall.common.result.Result;
import com.flashmall.pojo.entity.Goods;
import com.flashmall.pojo.entity.GoodsSku;
import com.flashmall.service.GoodsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * B 端商品管理
 * <p>
 * 保存/修改都会触发公共字段自动填充（@AutoFill + AOP），并清理商品详情缓存。
 */
@RestController
@RequestMapping("/api/admin/goods")
@RequiredArgsConstructor
public class AdminGoodsController {

    private final GoodsService goodsService;

    @GetMapping("/page")
    public Result<PageResult<Goods>> page(@RequestParam(required = false) Long categoryId,
                                          @RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long size) {
        return Result.ok(goodsService.adminPage(categoryId, page, size));
    }

    @PostMapping
    public Result<Long> save(@RequestBody Goods goods) {
        return Result.ok(goodsService.saveGoods(goods));
    }

    @PutMapping
    public Result<Void> update(@RequestBody Goods goods) {
        goodsService.updateGoods(goods);
        return Result.ok();
    }

    @PutMapping("/{goodsId}/status")
    public Result<Void> updateStatus(@PathVariable Long goodsId, @RequestParam Integer status) {
        goodsService.updateStatus(goodsId, status);
        return Result.ok();
    }

    @GetMapping("/{goodsId}/skus")
    public Result<List<GoodsSku>> skus(@PathVariable Long goodsId) {
        return Result.ok(goodsService.listSkuByGoodsId(goodsId));
    }

    @PostMapping("/sku")
    public Result<Long> saveSku(@RequestBody GoodsSku sku) {
        return Result.ok(goodsService.saveSku(sku));
    }
}
