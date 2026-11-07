package com.flashmall.controller;

import com.flashmall.common.result.Result;
import com.flashmall.pojo.dto.CartAddDTO;
import com.flashmall.pojo.entity.Cart;
import com.flashmall.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 购物车
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public Result<List<Cart>> list() {
        return Result.ok(cartService.list());
    }

    @GetMapping("/count")
    public Result<Long> count() {
        return Result.ok(cartService.count());
    }

    @PostMapping("/add")
    public Result<Void> add(@RequestBody @Valid CartAddDTO dto) {
        cartService.add(dto);
        return Result.ok();
    }

    @PutMapping("/{cartId}/quantity")
    public Result<Void> updateQuantity(@PathVariable Long cartId, @RequestParam Integer quantity) {
        cartService.updateQuantity(cartId, quantity);
        return Result.ok();
    }

    @DeleteMapping("/{cartId}")
    public Result<Void> remove(@PathVariable Long cartId) {
        cartService.remove(cartId);
        return Result.ok();
    }

    @DeleteMapping("/clear")
    public Result<Void> clear() {
        cartService.clear();
        return Result.ok();
    }
}
