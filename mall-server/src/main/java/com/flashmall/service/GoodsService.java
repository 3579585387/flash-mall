package com.flashmall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashmall.annotation.AutoFill;
import com.flashmall.common.constant.MessageConstant;
import com.flashmall.common.constant.RedisConstants;
import com.flashmall.common.enums.OperationType;
import com.flashmall.common.exception.BizException;
import com.flashmall.common.result.PageResult;
import com.flashmall.cache.CacheTemplate;
import com.flashmall.mapper.CategoryMapper;
import com.flashmall.mapper.GoodsMapper;
import com.flashmall.mapper.GoodsSkuMapper;
import com.flashmall.mapper.SeckillActivityMapper;
import com.flashmall.mapper.SeckillSkuMapper;
import com.flashmall.pojo.entity.Category;
import com.flashmall.pojo.entity.Goods;
import com.flashmall.pojo.entity.GoodsSku;
import com.flashmall.pojo.entity.SeckillActivity;
import com.flashmall.pojo.entity.SeckillSku;
import com.flashmall.pojo.vo.GoodsDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 商品
 * <p>
 * 读多写少，是缓存的主战场：详情走「Caffeine → Redis（逻辑过期）→ MySQL」三级缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsService {

    private final GoodsMapper goodsMapper;
    private final GoodsSkuMapper goodsSkuMapper;
    private final CategoryMapper categoryMapper;
    private final SeckillSkuMapper seckillSkuMapper;
    private final SeckillActivityMapper seckillActivityMapper;
    private final CacheTemplate cacheTemplate;

    /* ==================== 读 ==================== */

    public List<Category> listCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSort));
    }

    public PageResult<Goods> page(Long categoryId, long pageNo, long pageSize) {
        LambdaQueryWrapper<Goods> wrapper = new LambdaQueryWrapper<Goods>()
                .eq(Goods::getStatus, 1)
                .eq(categoryId != null, Goods::getCategoryId, categoryId)
                .orderByDesc(Goods::getSales);
        Page<Goods> page = goodsMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    /**
     * B 端商品分页（不做上架过滤，后台要能看到下架商品）
     */
    public PageResult<Goods> adminPage(Long categoryId, long pageNo, long pageSize) {
        LambdaQueryWrapper<Goods> wrapper = new LambdaQueryWrapper<Goods>()
                .eq(categoryId != null, Goods::getCategoryId, categoryId)
                .orderByDesc(Goods::getId);
        Page<Goods> page = goodsMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    /**
     * 商品详情
     * <p>
     * 逻辑过期方案要求缓存预热，这里做了兜底：冷启动未命中时回源并写入逻辑过期缓存。
     */
    public GoodsDetailVO detail(Long skuId) {
        String cacheKey = RedisConstants.GOODS_DETAIL_KEY + skuId;
        GoodsDetailVO vo = cacheTemplate.queryWithLogicalExpire(
                cacheKey, GoodsDetailVO.class, key -> loadDetail(skuId),
                RedisConstants.GOODS_DETAIL_TTL_MINUTES, TimeUnit.MINUTES);

        if (vo != null) {
            return vo;
        }
        // 命中空值标记：说明这个 skuId 不存在，直接返回，不要再查库
        if (cacheTemplate.isNullMarker(cacheKey)) {
            throw new BizException(MessageConstant.GOODS_NOT_FOUND);
        }

        vo = loadDetail(skuId);
        if (vo == null) {
            // 不存在的 skuId 也缓存一个短 TTL 的空值，防止缓存穿透
            cacheTemplate.setNull(cacheKey);
            throw new BizException(MessageConstant.GOODS_NOT_FOUND);
        }
        cacheTemplate.setWithLogicalExpire(cacheKey, vo,
                RedisConstants.GOODS_DETAIL_TTL_MINUTES, TimeUnit.MINUTES);
        return vo;
    }

    private GoodsDetailVO loadDetail(Long skuId) {
        GoodsSku sku = goodsSkuMapper.selectById(skuId);
        if (sku == null) {
            return null;
        }
        Goods goods = goodsMapper.selectById(sku.getGoodsId());
        if (goods == null) {
            return null;
        }

        GoodsDetailVO vo = new GoodsDetailVO();
        vo.setGoodsId(goods.getId());
        vo.setName(goods.getName());
        vo.setSubTitle(goods.getSubTitle());
        vo.setCoverUrl(goods.getCoverUrl());
        vo.setDetail(goods.getDetail());
        vo.setCategoryId(goods.getCategoryId());

        vo.setSkuId(sku.getId());
        vo.setSpecJson(sku.getSpecJson());
        vo.setPrice(sku.getPrice());
        vo.setStock(sku.getStock());

        // 关联进行中的秒杀活动（一个 SKU 同一时间只会在一个活动里）
        SeckillSku seckillSku = seckillSkuMapper.selectOne(new LambdaQueryWrapper<SeckillSku>()
                .eq(SeckillSku::getSkuId, skuId)
                .last("limit 1"));
        if (seckillSku != null) {
            SeckillActivity activity = seckillActivityMapper.selectById(seckillSku.getActivityId());
            if (activity != null && activity.getEndTime().isAfter(LocalDateTime.now())) {
                vo.setSeckillActivityId(activity.getId());
                vo.setSeckillPrice(seckillSku.getSeckillPrice());
                vo.setSeckillStock(seckillSku.getSeckillStock());
                vo.setLimitPerUser(seckillSku.getLimitPerUser());
            }
        }
        return vo;
    }

    /* ==================== 写（B 端） ==================== */

    @AutoFill(OperationType.INSERT)
    @Transactional(rollbackFor = Exception.class)
    public Long saveGoods(Goods goods) {
        if (goods.getStatus() == null) {
            goods.setStatus(1);
        }
        if (goods.getSales() == null) {
            goods.setSales(0);
        }
        if (goods.getMerchantId() == null) {
            goods.setMerchantId(1L);
        }
        goodsMapper.insert(goods);
        return goods.getId();
    }

    @AutoFill(OperationType.UPDATE)
    @Transactional(rollbackFor = Exception.class)
    public void updateGoods(Goods goods) {
        if (goods.getId() == null) {
            throw new BizException("商品 id 不能为空");
        }
        goodsMapper.updateById(goods);
        // 关键：改完必须清缓存，否则用户看到的还是旧价格
        invalidateCacheByGoodsId(goods.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long goodsId, Integer status) {
        Goods update = new Goods();
        update.setId(goodsId);
        update.setStatus(status);
        update.setUpdateTime(LocalDateTime.now());
        goodsMapper.updateById(update);
        invalidateCacheByGoodsId(goodsId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveSku(GoodsSku sku) {
        if (sku.getVersion() == null) {
            sku.setVersion(0);
        }
        if (sku.getStatus() == null) {
            sku.setStatus(1);
        }
        goodsSkuMapper.insert(sku);
        invalidateCacheByGoodsId(sku.getGoodsId());
        return sku.getId();
    }

    public List<GoodsSku> listSkuByGoodsId(Long goodsId) {
        return goodsSkuMapper.selectList(new LambdaQueryWrapper<GoodsSku>()
                .eq(GoodsSku::getGoodsId, goodsId));
    }

    /** 删除某个商品下所有 SKU 的详情缓存 */
    private void invalidateCacheByGoodsId(Long goodsId) {
        List<GoodsSku> skus = goodsSkuMapper.selectList(new LambdaQueryWrapper<GoodsSku>()
                .eq(GoodsSku::getGoodsId, goodsId));
        for (GoodsSku sku : skus) {
            cacheTemplate.invalidate(RedisConstants.GOODS_DETAIL_KEY + sku.getId());
        }
        log.debug("已清理商品 {} 的 {} 个 SKU 缓存", goodsId, skus.size());
    }
}
