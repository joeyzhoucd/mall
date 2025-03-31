package com.joeyzhoucd.product.service.impl;

import org.springframework.stereotype.Service;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.common.utils.Query;

import com.joeyzhoucd.product.dao.SkuBaseAttrValueDao;
import com.joeyzhoucd.product.entity.SkuBaseAttrValueEntity;
import com.joeyzhoucd.product.service.SkuBaseAttrValueService;


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