package com.mall.ware.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.ware.dao.WareSkuDao;
import com.mall.ware.entity.WareSkuEntity;
import com.mall.ware.service.WareSkuService;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuDao, WareSkuEntity> implements WareSkuService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        QueryWrapper<WareSkuEntity> wrapper = new QueryWrapper<>();

        // æ£€ç´¢æ¡ä»¶ï¼šæ”¯æŒIDã€SKU IDã€å•†å“åç§°æ¨¡ç³ŠæŸ¥è¯¢
        String key = (String) params.get("key");
        if (StringUtils.isNotBlank(key)) {
            wrapper.and(w -> {
                try {
                    Long id = Long.valueOf(key);
                    w.eq("id", id).or().eq("sku_id", id);
                } catch (NumberFormatException e) {
                    // å¦‚æžœä¸æ˜¯æ•°å­—ï¼Œåˆ™æŒ‰å•†å“åç§°æ¨¡ç³ŠæŸ¥è¯¢
                    w.like("sku_name", key);
                }
            });
        }

        // ä»“åº“ç­›é€‰
        Object wareId = params.get("wareId");
        if (wareId != null) {
            wrapper.eq("ware_id", wareId);
        }

        // åº“å­˜çŠ¶æ€ç­›é€‰
        Object stockStatus = params.get("stockStatus");
        if (stockStatus != null) {
            if ("inStock".equals(stockStatus)) {
                wrapper.gt("stock", 0);
            } else if ("outOfStock".equals(stockStatus)) {
                wrapper.eq("stock", 0);
            } else if ("lowStock".equals(stockStatus)) {
                wrapper.lt("stock", 10); // åº“å­˜å°‘äºŽ10ä¸ºä½Žåº“å­˜
            }
        }

        wrapper.orderByDesc("id"); // é»˜è®¤æŒ‰IDå€’åº

        IPage<WareSkuEntity> page = this.page(
                new Query<WareSkuEntity>().getPage(params),
                wrapper
        );

        return new PageUtils(page);
    }

    @Override
    public void addStock(Long skuId, Long wareId, Integer skuNum, String skuName) {
        QueryWrapper<WareSkuEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("sku_id", skuId).eq("ware_id", wareId);
        WareSkuEntity exist = this.getOne(wrapper);
        if (exist == null) {
            WareSkuEntity entity = new WareSkuEntity();
            entity.setSkuId(skuId);
            entity.setWareId(wareId);
            entity.setStock(skuNum == null ? 0 : skuNum);
            entity.setSkuName(skuName);
            entity.setStockLocked(0);
            this.save(entity);
        } else {
            int newStock = (exist.getStock() == null ? 0 : exist.getStock()) + (skuNum == null ? 0 : skuNum);
            exist.setStock(newStock);
            this.updateById(exist);
        }
    }
}
