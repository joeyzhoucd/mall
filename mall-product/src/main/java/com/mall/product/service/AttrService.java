package com.mall.product.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.product.entity.AttrEntity;
import com.mall.product.vo.AttrSaveRequestVO;

import java.util.List;
import java.util.Map;


public interface AttrService extends IService<AttrEntity> {

    
    PageUtils querySpecAttrPage(Map<String, Object> params);

    
    void saveBaseAttr(AttrSaveRequestVO req);

    
    void updateBaseAttr(AttrSaveRequestVO req);

    
    List<AttrEntity> queryUnRelatedAttr(Long attrgroupId);

    // ==================== Sale Attributes ====================

    
    PageUtils querySaleAttrPage(Map<String, Object> params);

    
    void saveSaleAttr(AttrSaveRequestVO req);

    
    void updateSaleAttr(AttrSaveRequestVO req);

    
    void deleteAttrWithRelations(Long attrId);

    
    void deleteAttrsWithRelations(Long[] attrIds);
}