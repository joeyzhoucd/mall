package com.joeyzhoucd.coupon.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.common.utils.Query;
import com.joeyzhoucd.coupon.dao.CategoryBoundsDao;
import com.joeyzhoucd.coupon.entity.CategoryBoundsEntity;
import com.joeyzhoucd.coupon.service.CategoryBoundsService;
import org.springframework.stereotype.Service;

import java.util.Map;


@Service("categoryBoundsService")
public class CategoryBoundsServiceImpl extends ServiceImpl<CategoryBoundsDao, CategoryBoundsEntity> implements CategoryBoundsService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryBoundsEntity> page = this.page(
                new Query<CategoryBoundsEntity>().getPage(params),
                new QueryWrapper<CategoryBoundsEntity>()
        );

        return new PageUtils(page);
    }

}