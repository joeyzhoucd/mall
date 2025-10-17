package com.mall.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.product.entity.CategoryBrandRelationEntity;

import java.util.List;
import java.util.Map;

/**
 * åˆ†ç±»&å“ç‰Œ åˆ†ç»„å…³è”
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
public interface CategoryBrandRelationService extends IService<CategoryBrandRelationEntity> {

    PageUtils queryPage(Map<String, Object> params);

    boolean deleteByBrandId(Long brandId);

    boolean updateBrandCategoryRelations(Long brandId, List<Long> categoryIds);

    List<CategoryBrandRelationEntity> getRelationsByBrandId(Long brandId);

    List<CategoryBrandRelationEntity> getRelationsByCategoryId(Long categoryId);
}

