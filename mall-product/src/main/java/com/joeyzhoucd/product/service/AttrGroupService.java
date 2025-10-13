package com.joeyzhoucd.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.product.entity.AttrGroupEntity;
import com.joeyzhoucd.product.vo.AttrGroupWithAttrVO;

import java.util.List;
import java.util.Map;

/**
 * 属性分组
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
public interface AttrGroupService extends IService<AttrGroupEntity> {

    PageUtils queryPage(Map<String, Object> params);

    List<AttrGroupWithAttrVO> getAttrGroupWithAttrs(Long categoryId);
}

