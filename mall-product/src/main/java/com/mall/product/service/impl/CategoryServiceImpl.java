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
        // 1. æŸ¥å‡ºæ‰€æœ‰åˆ†ç±»
        List<CategoryEntity> allCategories = baseMapper.selectList(null);

        if (CollectionUtils.isEmpty(allCategories)) {
            return Collections.emptyList();
        }

        // 2. æŒ‰ parentCid åˆ†ç»„
        Map<Long, List<CategoryEntity>> parentMap = allCategories.stream()
                .collect(Collectors.groupingBy(CategoryEntity::getParentCid));

        // 3. èŽ·å–ä¸€çº§åˆ†ç±» (parentCid = 0)
        List<CategoryEntity> rootCategories = parentMap.getOrDefault(0L, Collections.emptyList());

        // 4. é€’å½’æž„å»ºæ ‘
        rootCategories.forEach(cat -> setChildren(cat, parentMap));

        // 5. ä¸€çº§åˆ†ç±»æŒ‰ sort æŽ’åº
        rootCategories.sort(Comparator.comparingInt(o -> (o.getSort() == null ? 0 : o.getSort())));

        return rootCategories;
    }

    /**
     * é€’å½’è®¾ç½®å­åˆ†ç±»
     */
    private void setChildren(CategoryEntity parent, Map<Long, List<CategoryEntity>> parentMap) {
        List<CategoryEntity> children = parentMap.getOrDefault(parent.getCatId(), Collections.emptyList());

        // å­åˆ†ç±»é€’å½’è®¾ç½®
        children.forEach(child -> setChildren(child, parentMap));

        // æŽ’åº
        children.sort(Comparator.comparingInt(o -> (o.getSort() == null ? 0 : o.getSort())));

        parent.setChildren(children);
    }


}
