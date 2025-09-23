package com.joeyzhoucd.coupon.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.common.utils.Query;
import com.joeyzhoucd.coupon.dao.SkuBoundsDao;
import com.joeyzhoucd.coupon.entity.SkuBoundsEntity;
import com.joeyzhoucd.coupon.service.SkuBoundsService;
import org.springframework.stereotype.Service;

import java.util.Map;


@Service("skuBoundsService")
public class SkuBoundsServiceImpl extends ServiceImpl<SkuBoundsDao, SkuBoundsEntity> implements SkuBoundsService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SkuBoundsEntity> page = this.page(
                new Query<SkuBoundsEntity>().getPage(params),
                new QueryWrapper<SkuBoundsEntity>()
        );

        return new PageUtils(page);
    }

}