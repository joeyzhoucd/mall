package com.joeyzhoucd.member.controller;

import com.joeyzhoucd.common.utils.R;
import com.joeyzhoucd.member.entity.MemberEntity;
import com.joeyzhoucd.member.feign.CouponFeignService;
import com.joeyzhoucd.member.service.MemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员控制器
 * 保留基础结构，删除前端未使用的方法
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:25:02
 */
@RestController
@RequestMapping("member/member")
public class MemberController {
    @Autowired
    private MemberService memberService;

    @Autowired
    private CouponFeignService couponFeignService;

    /**
     * 测试接口 - 获取会员优惠券信息
     */
    @RequestMapping("/coupons")
    public R test() {
        MemberEntity memberEntity = new MemberEntity();
        memberEntity.setNickname("张三");
        R membercoupons = couponFeignService.memberCoupons();
        return R.ok().put("member", memberEntity).put("coupons", membercoupons.get("coupons"));
    }

    /**
     * 预留接口 - 会员功能待开发
     */
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "会员功能待开发");
    }
}
