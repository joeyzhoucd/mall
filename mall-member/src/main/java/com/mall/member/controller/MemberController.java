package com.mall.member.controller;

import com.mall.common.utils.R;
import com.mall.member.entity.MemberEntity;
import com.mall.member.feign.CouponFeignService;
import com.mall.member.service.MemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("member/member")
public class MemberController {
    @Autowired
    private MemberService memberService;

    @Autowired
    private CouponFeignService couponFeignService;

    
    @RequestMapping("/coupons")
    public R test() {
        MemberEntity memberEntity = new MemberEntity();
        memberEntity.setNickname("Test User");
        R membercoupons = couponFeignService.memberCoupons();
        return R.ok().put("member", memberEntity).put("coupons", membercoupons.get("coupons"));
    }

    
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "This is a placeholder method");
    }
}