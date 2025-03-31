package com.joeyzhoucd.coupon.dao;

import com.joeyzhoucd.coupon.entity.CouponHistoryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 优惠券领取历史记录
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:08:26
 */
@Mapper
public interface CouponHistoryDao extends BaseMapper<CouponHistoryEntity> {
	
}
