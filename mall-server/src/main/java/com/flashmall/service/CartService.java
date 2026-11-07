package com.flashmall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.flashmall.common.constant.MessageConstant;
import com.flashmall.common.exception.BizException;
import com.flashmall.common.utils.UserHolder;
import com.flashmall.mapper.CartMapper;
import com.flashmall.mapper.GoodsMapper;
import com.flashmall.mapper.GoodsSkuMapper;
import com.flashmall.pojo.dto.CartAddDTO;
import com.flashmall.pojo.entity.Cart;
import com.flashmall.pojo.entity.Goods;
import com.flashmall.pojo.entity.GoodsSku;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 购物车
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartMapper cartMapper;
    private final GoodsSkuMapper goodsSkuMapper;
    private final GoodsMapper goodsMapper;

    /**
     * 加入购物车：同一 SKU 重复添加则累加数量
     * <p>
     * 依赖 (user_id, sku_id) 唯一索引保证同一用户同一 SKU 只有一行。
     */
    @Transactional(rollbackFor = Exception.class)
    public void add(CartAddDTO dto) {
        Long userId = UserHolder.requireUserId();

        GoodsSku sku = goodsSkuMapper.selectById(dto.getSkuId());
        if (sku == null) {
            throw new BizException(MessageConstant.GOODS_NOT_FOUND);
        }
        Goods goods = goodsMapper.selectById(sku.getGoodsId());
        if (goods == null || goods.getStatus() == null || goods.getStatus() != 1) {
            throw new BizException(MessageConstant.GOODS_OFF_SHELF);
        }

        Cart exist = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getSkuId, dto.getSkuId()));

        int quantity = dto.getQuantity();
        if (exist != null) {
            int total = exist.getQuantity() + quantity;
            if (total > sku.getStock()) {
                throw new BizException(MessageConstant.STOCK_NOT_ENOUGH);
            }
            cartMapper.update(null, new LambdaUpdateWrapper<Cart>()
                    .set(Cart::getQuantity, total)
                    .eq(Cart::getId, exist.getId()));
            return;
        }
        if (sku.getStock() < quantity) {
            throw new BizException(MessageConstant.STOCK_NOT_ENOUGH);
        }

        Cart cart = new Cart();
        cart.setUserId(userId);
        cart.setSkuId(sku.getId());
        cart.setGoodsId(goods.getId());
        cart.setGoodsName(goods.getName());
        cart.setSpecJson(sku.getSpecJson());
        cart.setCoverUrl(goods.getCoverUrl());
        cart.setPrice(sku.getPrice());
        cart.setQuantity(quantity);
        cart.setCreateTime(LocalDateTime.now());
        cartMapper.insert(cart);
    }

    public List<Cart> list() {
        Long userId = UserHolder.requireUserId();
        return cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .orderByDesc(Cart::getCreateTime));
    }

    /** 购物车条数（前端角标） */
    public long count() {
        Long userId = UserHolder.requireUserId();
        Long count = cartMapper.selectCount(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId));
        return count == null ? 0L : count;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateQuantity(Long cartId, Integer quantity) {
        Long userId = UserHolder.requireUserId();
        if (quantity == null || quantity < 1) {
            throw new BizException("数量必须大于 0");
        }
        Cart cart = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getId, cartId)
                .eq(Cart::getUserId, userId));
        if (cart == null) {
            throw new BizException("购物车记录不存在");
        }
        GoodsSku sku = goodsSkuMapper.selectById(cart.getSkuId());
        if (sku == null || sku.getStock() < quantity) {
            throw new BizException(MessageConstant.STOCK_NOT_ENOUGH);
        }
        cartMapper.update(null, new LambdaUpdateWrapper<Cart>()
                .set(Cart::getQuantity, quantity)
                .eq(Cart::getId, cartId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long cartId) {
        Long userId = UserHolder.requireUserId();
        cartMapper.delete(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getId, cartId)
                .eq(Cart::getUserId, userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void clear() {
        Long userId = UserHolder.requireUserId();
        cartMapper.delete(new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, userId));
    }
}
