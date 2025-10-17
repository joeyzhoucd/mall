package com.mall.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.product.entity.AttrGroupEntity;
import com.mall.product.vo.AttrGroupWithAttrVO;

import java.util.List;
import java.util.Map;

/**
 * å±žæ€§åˆ†ç»„
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
public interface AttrGroupService extends IService<AttrGroupEntity> {

    PageUtils queryPage(Map<String, Object> params);

    List<AttrGroupWithAttrVO> getAttrGroupWithAttrs(Long categoryId);
}

