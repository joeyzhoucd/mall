package com.mall.coupon.controller;

import com.mall.common.utils.R;
import com.mall.coupon.entity.CouponEntity;
import com.mall.coupon.service.CouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import com.mall.common.utils.PageUtils;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Map;




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

    /**
     * 优惠券分页列表。后台在给 SKU 绑券时用它填下拉框。
     */
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params) {
        PageUtils page = couponService.queryPage(params);
        return R.ok().put("page", page);
    }

}