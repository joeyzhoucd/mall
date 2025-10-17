package com.mall.ware.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.ware.dao.PurchaseDetailDao;
import com.mall.ware.entity.PurchaseDetailEntity;
import com.mall.ware.service.PurchaseDetailService;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service("purchaseDetailService")
public class PurchaseDetailServiceImpl extends ServiceImpl<PurchaseDetailDao, PurchaseDetailEntity> implements PurchaseDetailService {
    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        QueryWrapper<PurchaseDetailEntity> wrapper = new QueryWrapper<>();

        // é‡‡è´­å•IDç­›é€‰
        Object purchaseId = params.get("purchaseId");
        if (purchaseId != null && StringUtils.isNotBlank(purchaseId.toString())) {
            wrapper.eq("purchase_id", purchaseId);
        }

        // ä»“åº“IDç­›é€‰
        Object wareId = params.get("wareId");
        if (wareId != null && StringUtils.isNotBlank(wareId.toString())) {
            wrapper.eq("ware_id", wareId);
        }

        // çŠ¶æ€ç­›é€‰
        Object status = params.get("status");
        if (status != null && StringUtils.isNotBlank(status.toString())) {
            wrapper.eq("status", status);
        }

        // å…³é”®è¯æœç´¢ï¼ˆæ”¯æŒIDã€SKU IDã€é‡‡è´­å•IDï¼‰
        String key = (String) params.get("key");
        if (StringUtils.isNotBlank(key)) {
            wrapper.and(w -> {
                try {
                    Long id = Long.valueOf(key);
                    w.eq("id", id).or().eq("sku_id", id).or().eq("purchase_id", id);
                } catch (NumberFormatException e) {
                    // å¦‚æžœä¸æ˜¯æ•°å­—ï¼Œåˆ™æŒ‰SKUåç§°æ¨¡ç³ŠæŸ¥è¯¢
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
