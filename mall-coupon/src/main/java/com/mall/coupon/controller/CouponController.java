package com.mall.coupon.controller;

import com.mall.common.utils.R;
import com.mall.coupon.entity.CouponEntity;
import com.mall.coupon.service.CouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;



/**
 * ä¼˜æƒ åˆ¸ä¿¡æ¯
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
        couponEntity.setCouponName("æ»¡100å‡10");
        return R.ok().put("coupons", Arrays.asList(couponEntity));
    }

    /**
     * é¢„ç•™æŽ¥å£ - ä¼˜æƒ åˆ¸åŠŸèƒ½å¾…å¼€å‘
     */
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "ä¼˜æƒ åˆ¸åŠŸèƒ½å¾…å¼€å‘");
    }
}
