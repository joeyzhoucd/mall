package com.mall.member.controller;

import com.mall.common.utils.R;
import com.mall.member.entity.MemberEntity;
import com.mall.member.exception.PhoneExistException;
import com.mall.member.exception.UsernameExistException;
import com.mall.member.feign.CouponFeignService;
import com.mall.member.service.MemberService;
import com.mall.member.vo.MemberLoginVo;
import com.mall.member.vo.MemberRegistVo;
import com.mall.member.vo.MemberRespVo;
import com.mall.member.vo.SocialUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/register")
    public R register(@RequestBody MemberRegistVo vo) {
        try {
            memberService.register(vo);
        } catch (PhoneExistException e) {
            return R.error(15002, e.getMessage());
        } catch (UsernameExistException e) {
            return R.error(15001, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return R.error(15000, "User exists or unknown error: " + e.getMessage());
        }
        return R.ok();
    }

    @PostMapping("/login")
    public R login(@RequestBody MemberLoginVo vo) {
        MemberEntity entity = memberService.login(vo);
        if (entity != null) {
            MemberRespVo respVo = new MemberRespVo();
            respVo.setId(entity.getId());
            respVo.setUsername(entity.getUsername());
            respVo.setNickname(entity.getNickname());
            respVo.setMobile(entity.getMobile());
            respVo.setLevelId(entity.getLevelId());
            // MemberEntity 使用 header 字段存头像
            respVo.setIcon(entity.getHeader());
            return R.ok().put("member", respVo);
        } else {
            // 统一错误文案，避免信息泄露
            return R.error(15003, "账号或密码错误");
        }
    }

    @PostMapping("/oauth2/login")
    public R oauthLogin(@RequestBody SocialUser socialUser) {
        MemberEntity entity = memberService.login(socialUser);
        if (entity != null) {
            MemberRespVo respVo = new MemberRespVo();
            respVo.setId(entity.getId());
            respVo.setUsername(entity.getUsername());
            respVo.setNickname(entity.getNickname());
            respVo.setMobile(entity.getMobile());
            respVo.setLevelId(entity.getLevelId());
            respVo.setIcon(entity.getHeader());
            return R.ok().put("member", respVo);
        } else {
            return R.error(15003, "账号或密码错误");
        }
    }
}