package com.mall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.product.dao.SkuBaseAttrValueDao;
import com.mall.product.entity.SkuBaseAttrValueEntity;
import com.mall.product.service.SkuBaseAttrValueService;
import org.springframework.stereotype.Service;

import java.util.Map;


@Service("skuBaseAttrValueService")
public class SkuBaseAttrValueServiceImpl extends ServiceImpl<SkuBaseAttrValueDao, SkuBaseAttrValueEntity> implements SkuBaseAttrValueService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SkuBaseAttrValueEntity> page = this.page(
                new Query<SkuBaseAttrValueEntity>().getPage(params),
                new QueryWrapper<SkuBaseAttrValueEntity>()
        );

        return new PageUtils(page);
    }

}
