package com.joeyzhoucd.ware.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.common.utils.Query;
import com.joeyzhoucd.ware.dao.PurchaseDetailDao;
import com.joeyzhoucd.ware.entity.PurchaseDetailEntity;
import com.joeyzhoucd.ware.service.PurchaseDetailService;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service("purchaseDetailService")
public class PurchaseDetailServiceImpl extends ServiceImpl<PurchaseDetailDao, PurchaseDetailEntity> implements PurchaseDetailService {
    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        QueryWrapper<PurchaseDetailEntity> wrapper = new QueryWrapper<>();

        // 采购单ID筛选
        Object purchaseId = params.get("purchaseId");
        if (purchaseId != null && StringUtils.isNotBlank(purchaseId.toString())) {
            wrapper.eq("purchase_id", purchaseId);
        }

        // 仓库ID筛选
        Object wareId = params.get("wareId");
        if (wareId != null && StringUtils.isNotBlank(wareId.toString())) {
            wrapper.eq("ware_id", wareId);
        }

        // 状态筛选
        Object status = params.get("status");
        if (status != null && StringUtils.isNotBlank(status.toString())) {
            wrapper.eq("status", status);
        }

        // 关键词搜索（支持ID、SKU ID、采购单ID）
        String key = (String) params.get("key");
        if (StringUtils.isNotBlank(key)) {
            wrapper.and(w -> {
                try {
                    Long id = Long.valueOf(key);
                    w.eq("id", id).or().eq("sku_id", id).or().eq("purchase_id", id);
                } catch (NumberFormatException e) {
                    // 如果不是数字，则按SKU名称模糊查询
                    w.like("sku_name", key);
                }
            });
        }

        wrapper.orderByDesc("id");

        IPage<PurchaseDetailEntity> page = this.page(
                new Query<PurchaseDetailEntity>().getPage(params),
                wrapper
        );
        return new PageUtils(page);
    }
}