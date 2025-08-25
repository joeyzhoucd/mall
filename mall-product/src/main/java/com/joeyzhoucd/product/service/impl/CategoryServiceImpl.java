package com.joeyzhoucd.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.common.utils.Query;
import com.joeyzhoucd.product.dao.CategoryDao;
import com.joeyzhoucd.product.entity.CategoryEntity;
import com.joeyzhoucd.product.service.CategoryService;
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
        // 1. 查出所有分类
        List<CategoryEntity> allCategories = baseMapper.selectList(null);

        if (CollectionUtils.isEmpty(allCategories)) {
            return Collections.emptyList();
        }

        // 2. 按 parentCid 分组
        Map<Long, List<CategoryEntity>> parentMap = allCategories.stream()
                .collect(Collectors.groupingBy(CategoryEntity::getParentCid));

        // 3. 获取一级分类 (parentCid = 0)
        List<CategoryEntity> rootCategories = parentMap.getOrDefault(0L, Collections.emptyList());

        // 4. 递归构建树
        rootCategories.forEach(cat -> setChildren(cat, parentMap));

        // 5. 一级分类按 sort 排序
        rootCategories.sort(Comparator.comparingInt(o -> (o.getSort() == null ? 0 : o.getSort())));

        return rootCategories;
    }

    /**
     * 递归设置子分类
     */
    private void setChildren(CategoryEntity parent, Map<Long, List<CategoryEntity>> parentMap) {
        List<CategoryEntity> children = parentMap.getOrDefault(parent.getCatId(), Collections.emptyList());

        // 子分类递归设置
        children.forEach(child -> setChildren(child, parentMap));

        // 排序
        children.sort(Comparator.comparingInt(o -> (o.getSort() == null ? 0 : o.getSort())));

        parent.setChildren(children);
    }


}