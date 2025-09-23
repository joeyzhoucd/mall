package com.joeyzhoucd.coupon.controller;

import com.joeyzhoucd.common.utils.R;
import com.joeyzhoucd.coupon.entity.CouponEntity;
import com.joeyzhoucd.coupon.service.CouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;



/**
 * 优惠券信息
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:08:26
 */
@RestController
@RequestMapping("coupon/coupon")
public class CouponController {
    @Autowired
    private CouponService couponService;

    @RequestMapping("/member/list")
    public R membercoupons() {
        CouponEntity couponEntity = new CouponEntity();
        couponEntity.setCouponName("满100减10");
        return R.ok().put("coupons", Arrays.asList(couponEntity));
    }

    /**
     * 预留接口 - 优惠券功能待开发
     */
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "优惠券功能待开发");
    }
}
