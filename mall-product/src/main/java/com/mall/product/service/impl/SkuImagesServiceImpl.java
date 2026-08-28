package com.mall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.product.dao.SkuImagesDao;
import com.mall.product.entity.SkuImagesEntity;
import com.mall.product.service.SkuImagesService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service("skuImagesService")
public class SkuImagesServiceImpl extends ServiceImpl<SkuImagesDao, SkuImagesEntity> implements SkuImagesService {

    /**
     * 按 skuId 分页查图片。
     *
     * <h3>skuId 这个条件不是可选的优化，漏掉会删错数据</h3>
     * 这个方法原来直接 {@code new QueryWrapper<>()}，把传进来的 skuId 丢掉了 ——
     * 分页返回的是<b>全库</b>的 sku 图片。
     * <p>
     * 单看这个方法只是「查得不准」，但后台的图片编辑是这样用它的
     * （mall-frontend product/sku.vue 的 submitUploadImages）：
     * <pre>
     *   查 /skuimages/list?skuId=X  ->  把查到的 id 全部 /skuimages/delete  ->  重新 save
     * </pre>
     * 条件丢了的话第一步返回的是别的 SKU 的图片，第二步就把它们删了。
     * 用户看到的只是「我编辑了 A 商品的图」，B 商品的图无声消失。
     * <p>
     * 这个洞一直没爆是因为 /skuimages/list 这个接口<b>之前压根不存在</b>，前端调了 404。
     * 补接口的同时必须把它一起修掉，否则等于把一个数据破坏的开关打开。
     */
    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        QueryWrapper<SkuImagesEntity> wrapper = new QueryWrapper<>();

        Object skuIdObj = params.get("skuId");
        if (skuIdObj != null && !String.valueOf(skuIdObj).isBlank()) {
            try {
                wrapper.eq("sku_id", Long.valueOf(String.valueOf(skuIdObj).trim()));
            } catch (NumberFormatException e) {
                // skuId 传了但不是数字：返回空集，绝不退化成「查全库」。
                // 上面说的删除流程会拿这个结果去删，宁可查不到也不能查多。
                wrapper.eq("sku_id", -1L);
            }
        }

        wrapper.orderByAsc("img_sort");

        IPage<SkuImagesEntity> page = this.page(new Query<SkuImagesEntity>().getPage(params), wrapper);
        return new PageUtils(page);
    }
}
