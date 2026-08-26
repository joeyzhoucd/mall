package com.mall.product.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.product.entity.CategoryBrandRelationEntity;

import java.util.List;
import java.util.Map;


public interface CategoryBrandRelationService extends IService<CategoryBrandRelationEntity> {

    PageUtils queryPage(Map<String, Object> params);

    boolean deleteByBrandId(Long brandId);

    boolean updateBrandCategoryRelations(Long brandId, List<Long> categoryIds);

    List<CategoryBrandRelationEntity> getRelationsByBrandId(Long brandId);

    List<CategoryBrandRelationEntity> getRelationsByCategoryId(Long categoryId);
}
