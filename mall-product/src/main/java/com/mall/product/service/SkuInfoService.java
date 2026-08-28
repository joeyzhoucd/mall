package com.mall.product.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.product.entity.SkuInfoEntity;
import com.mall.product.vo.SkuInfoVo;

import java.util.Map;


public interface SkuInfoService extends IService<SkuInfoEntity> {

    PageUtils queryPage(Map<String, Object> params);
    
    
    PageUtils queryPageWithDetails(Map<String, Object> params);

    com.mall.product.vo.SkuItemVo item(Long skuId);

    /**
     * 删除 SKU，连同它的图片和销售属性。
     *
     * <p>必须级联。只删 pms_sku_info 的话，pms_sku_images 和 pms_sku_sale_attr_value
     * 里会留下指向不存在 SKU 的行 —— 这些表没有外键约束，数据库不会拦。
     * 孤儿行不报错，只是让后面所有按 sku_id 聚合的查询都多算一份，
     * 而且新 SKU 一旦复用了同一个自增 id，会凭空继承上一个 SKU 的图片。
     */
    void removeSkus(java.util.List<Long> skuIds);

    /**
     * 更新 SKU 的基础信息（名称/标题/副标题/价格）。
     *
     * <p><b>只认白名单里的这四个字段</b>，不把请求体整个交给 updateById。
     * 请求体是客户端完全可控的 JSON，而 SkuInfoEntity 还有 spuId、brandId、
     * categoryId、saleCount 这些字段 —— 一次「改个标题」的请求可以顺手把商品
     * 挪到别的品牌下，或者把销量刷成任意数字。后台按钮不会这么做，
     * 但接口不该假设调用方只有那个按钮。
     *
     * <p>顺带说明一个<b>不</b>成立的担心：MyBatis-Plus 默认字段策略是 NOT_NULL，
     * updateById 只会为非 null 字段生成 SET，所以传半个对象【不会】把其余列写成 null。
     * 这里做白名单是为了防越权写入，不是为了防 null 覆盖 —— 两者容易混淆，
     * 一旦有人把默认策略改成 ALWAYS，那才轮到 null 覆盖的问题。
     */
    void updateBasicInfo(SkuInfoEntity sku);

    /** 批量上下架，返回实际更新的行数。 */
    int batchPublish(java.util.List<Long> skuIds, Integer publishStatus);

}
