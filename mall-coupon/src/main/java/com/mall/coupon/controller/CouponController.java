package com.mall.coupon.controller;

import com.mall.common.utils.R;
import com.mall.coupon.entity.CouponEntity;
import com.mall.coupon.service.CouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;




@RestController
@RequestMapping("coupon/coupon")
public class CouponController {
    @Autowired
    private CouponService couponService;

    @RequestMapping("/member/list")
    public R membercoupons() {
        CouponEntity couponEntity = new CouponEntity();
        couponEntity.setCouponName("Full 100 off 10");
        return R.ok().put("coupons", Arrays.asList(couponEntity));
    }

    
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "This is a placeholder method");
    }
}