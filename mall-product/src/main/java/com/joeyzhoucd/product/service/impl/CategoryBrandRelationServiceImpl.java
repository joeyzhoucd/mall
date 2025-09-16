package com.joeyzhoucd.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.common.utils.Query;
import com.joeyzhoucd.product.dao.CategoryBrandRelationDao;
import com.joeyzhoucd.product.entity.BrandEntity;
import com.joeyzhoucd.product.entity.CategoryBrandRelationEntity;
import com.joeyzhoucd.product.entity.CategoryEntity;
import com.joeyzhoucd.product.service.BrandService;
import com.joeyzhoucd.product.service.CategoryBrandRelationService;
import com.joeyzhoucd.product.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service("categoryBrandRelationService")
public class CategoryBrandRelationServiceImpl extends ServiceImpl<CategoryBrandRelationDao, CategoryBrandRelationEntity> implements CategoryBrandRelationService {

    @Autowired
    BrandService brandService;

    @Autowired
    CategoryService categoryService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryBrandRelationEntity> page = this.page(
                new Query<CategoryBrandRelationEntity>().getPage(params),
                new QueryWrapper<>()
        );

        return new PageUtils(page);
    }

    @Override
    public boolean deleteByBrandId(Long brandId) {

        // 1. 参数校验，避免传入空值
        Assert.notNull(brandId, "brandId must not be null");

        // 2. 构造删除条件
        QueryWrapper<CategoryBrandRelationEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("brand_id", brandId);

        // 3. 调用 MyBatis-Plus 自带的 remove 方法执行删除
        return this.remove(wrapper);
    }


    @Override
    public boolean deleteByCategoryId(Long categoryId) {

        // 1. 参数校验，避免传入空值
        Assert.notNull(categoryId, "categoryId must not be null");

        // 2. 构造删除条件
        QueryWrapper<CategoryBrandRelationEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("category_id", categoryId);

        // 3. 调用 MyBatis-Plus 自带的 remove 方法执行删除
        return this.remove(wrapper);
    }

    @Override
    @Transactional
    public boolean updateBrandCategoryRelations(Long brandId, List<Long> categoryIds) {
        if (brandId == null) {
            return false;
        }
        BrandEntity brand = brandService.getById(brandId);
        Assert.notNull(brand, "brand must be existing");
        // 1. 构造查询条件
        QueryWrapper<CategoryBrandRelationEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("brand_id", brandId);
        long count = this.count(wrapper);
        boolean success = true;
        if (count > 0) {
            success = deleteByBrandId(brandId);
        }
        if (!success) {
            return success;
        }
        List<CategoryBrandRelationEntity> categoryBrandRelations = categoryIds.stream().map(id -> {
            CategoryEntity category = categoryService.getById(id);
            if (category != null) {
                CategoryBrandRelationEntity entity = new CategoryBrandRelationEntity();
                entity.setBrandId(brandId);
                entity.setCategoryId(id);
                entity.setBrandName(brand.getName());
                entity.setCategoryName(category.getName());
                return entity;
            }
            return null;
        }).filter(entity -> entity != null).collect(Collectors.toList());
        return this.saveBatch(categoryBrandRelations);
    }

    @Override
    public List<CategoryBrandRelationEntity> getRelationsByBrandId(Long brandId) {
        // 1. 参数校验，避免传入空值
        Assert.notNull(brandId, "brandId must not be null");
        // 2. 构造查询条件
        QueryWrapper<CategoryBrandRelationEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("brand_id", brandId);
        return this.list(wrapper);
    }

}