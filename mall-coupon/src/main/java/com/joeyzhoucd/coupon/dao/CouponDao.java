package com.joeyzhoucd.coupon.dao;

import com.joeyzhoucd.coupon.entity.CouponEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 优惠券信息
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:08:26
 */
@Mapper
public interface CouponDao extends BaseMapper<CouponEntity> {
	
}
