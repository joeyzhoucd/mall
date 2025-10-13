package com.joeyzhoucd.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.product.entity.CategoryBrandRelationEntity;

import java.util.List;
import java.util.Map;

/**
 * 分类&品牌 分组关联
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

