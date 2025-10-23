package com.mall.coupon.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.coupon.entity.CouponEntity;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface CouponDao extends BaseMapper<CouponEntity> {
	
}
