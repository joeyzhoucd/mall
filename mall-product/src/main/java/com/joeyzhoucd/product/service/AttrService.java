package com.joeyzhoucd.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.product.entity.AttrEntity;
import com.joeyzhoucd.product.vo.AttrSaveRequestVO;

import java.util.List;
import java.util.Map;

/**
 * 商品属性服务接口
 * 专门处理规格参数相关的业务逻辑
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
public interface AttrService extends IService<AttrEntity> {

    /**
     * 获取规格参数分页列表
     */
    PageUtils querySpecAttrPage(Map<String, Object> params);

    /**
     * 新增规格参数
     */
    void saveBaseAttr(AttrSaveRequestVO req);

    /**
     * 修改规格参数
     */
    void updateBaseAttr(AttrSaveRequestVO req);

    /**
     * 获取未关联的属性列表
     */
    List<AttrEntity> queryUnRelatedAttr(Long attrgroupId);

    // ==================== 销售属性相关方法 ====================

    /**
     * 获取销售属性分页列表
     */
    PageUtils querySaleAttrPage(Map<String, Object> params);

    /**
     * 新增销售属性
     */
    void saveSaleAttr(AttrSaveRequestVO req);

    /**
     * 修改销售属性
     */
    void updateSaleAttr(AttrSaveRequestVO req);

    /**
     * 删除属性及其关联关系
     */
    void deleteAttrWithRelations(Long attrId);

    /**
     * 批量删除属性及其关联关系
     */
    void deleteAttrsWithRelations(Long[] attrIds);
}

