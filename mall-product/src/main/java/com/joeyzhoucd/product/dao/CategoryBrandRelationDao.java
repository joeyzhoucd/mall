package com.joeyzhoucd.product.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.joeyzhoucd.product.entity.CategoryBrandRelationEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分类&品牌分组关联
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@Mapper
public interface CategoryBrandRelationDao extends BaseMapper<CategoryBrandRelationEntity> {

}
