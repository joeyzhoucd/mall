package com.mall.coupon.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.coupon.entity.CouponEntity;

import java.util.Map;


public interface CouponService extends IService<CouponEntity> {

    PageUtils queryPage(Map<String, Object> params);
}
