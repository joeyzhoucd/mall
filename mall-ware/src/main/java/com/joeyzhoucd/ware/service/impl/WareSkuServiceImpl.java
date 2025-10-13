package com.joeyzhoucd.ware.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.common.utils.Query;
import com.joeyzhoucd.ware.dao.WareSkuDao;
import com.joeyzhoucd.ware.entity.WareSkuEntity;
import com.joeyzhoucd.ware.service.WareSkuService;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuDao, WareSkuEntity> implements WareSkuService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        QueryWrapper<WareSkuEntity> wrapper = new QueryWrapper<>();

        // 检索条件：支持ID、SKU ID、商品名称模糊查询
        String key = (String) params.get("key");
        if (StringUtils.isNotBlank(key)) {
            wrapper.and(w -> {
                try {
                    Long id = Long.valueOf(key);
                    w.eq("id", id).or().eq("sku_id", id);
                } catch (NumberFormatException e) {
                    // 如果不是数字，则按商品名称模糊查询
                    w.like("sku_name", key);
                }
            });
        }

        // 仓库筛选
        Object wareId = params.get("wareId");
        if (wareId != null) {
            wrapper.eq("ware_id", wareId);
        }

        // 库存状态筛选
        Object stockStatus = params.get("stockStatus");
        if (stockStatus != null) {
            if ("inStock".equals(stockStatus)) {
                wrapper.gt("stock", 0);
            } else if ("outOfStock".equals(stockStatus)) {
                wrapper.eq("stock", 0);
            } else if ("lowStock".equals(stockStatus)) {
                wrapper.lt("stock", 10); // 库存少于10为低库存
            }
        }

        wrapper.orderByDesc("id"); // 默认按ID倒序

        IPage<WareSkuEntity> page = this.page(
                new Query<WareSkuEntity>().getPage(params),
                wrapper
        );

        return new PageUtils(page);
    }
}