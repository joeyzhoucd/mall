package com.mall.product.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.product.entity.CategoryEntity;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;


public interface CategoryService extends IService<CategoryEntity> {

    PageUtils queryPage(Map<String, Object> params);

    List<CategoryEntity> listAsTree();

    boolean saveBatch(List<CategoryEntity> entityList);

    boolean removeByIds(List<?> idList);

    /**
     * 数一数：有多少个<b>未被删除</b>的分类，它的父分类在 {@code catIds} 里，
     * 而它自己<b>不在</b> {@code catIds} 里。
     *
     * 「自己不在里面」这个条件是关键 —— 整棵子树一起选中删除是合法的，
     * 只有「删了父、留下子」才会产生看不见的孤儿分类。
     *
     * @return 会被留成孤儿的分类数量，0 表示这批删除是安全的
     */
    long countChildrenOutside(List<Long> catIds);
}
