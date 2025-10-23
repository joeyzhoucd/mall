package com.mall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.product.dao.CategoryDao;
import com.mall.product.entity.CategoryEntity;
import com.mall.product.service.CategoryService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, CategoryEntity> implements CategoryService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryEntity> page = this.page(
                new Query<CategoryEntity>().getPage(params),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageUtils(page);
    }

    public List<CategoryEntity> listAsTree() {
        // 1. Get all categories
        List<CategoryEntity> allCategories = baseMapper.selectList(null);

        if (CollectionUtils.isEmpty(allCategories)) {
            return Collections.emptyList();
        }

        // 2. Group by parentCid
        Map<Long, List<CategoryEntity>> parentMap = allCategories.stream()
                .collect(Collectors.groupingBy(CategoryEntity::getParentCid));

        // 3. Get root categories (parentCid = 0)
        List<CategoryEntity> rootCategories = parentMap.getOrDefault(0L, Collections.emptyList());

        // 4. Set children recursively
        rootCategories.forEach(cat -> setChildren(cat, parentMap));

        // 5. Sort root categories by sort
        rootCategories.sort(Comparator.comparingInt(o -> (o.getSort() == null ? 0 : o.getSort())));

        return rootCategories;
    }

    
    private void setChildren(CategoryEntity parent, Map<Long, List<CategoryEntity>> parentMap) {
        List<CategoryEntity> children = parentMap.getOrDefault(parent.getCatId(), Collections.emptyList());

        // Set children recursively
        children.forEach(child -> setChildren(child, parentMap));

        // Sort
        children.sort(Comparator.comparingInt(o -> (o.getSort() == null ? 0 : o.getSort())));

        parent.setChildren(children);
    }


}