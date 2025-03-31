package com.joeyzhoucd.product.dao;

import com.joeyzhoucd.product.entity.CategoryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品三级分类
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@Mapper
public interface CategoryDao extends BaseMapper<CategoryEntity> {
	
}
