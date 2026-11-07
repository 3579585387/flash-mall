package com.flashmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashmall.pojo.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品分类
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
