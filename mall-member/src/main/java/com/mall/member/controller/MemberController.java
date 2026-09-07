package com.mall.member.controller;

import com.mall.common.utils.R;
import com.mall.member.entity.MemberEntity;
import com.mall.member.exception.PhoneExistException;
import com.mall.member.exception.UsernameExistException;
import com.mall.member.feign.CouponFeignService;
import com.mall.member.service.MemberService;
import com.mall.member.vo.MemberAdminVo;
import com.mall.member.vo.MemberLoginVo;
import com.mall.member.vo.MemberRegistVo;
import com.mall.member.vo.MemberRespVo;
import com.mall.member.vo.SocialUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("member/member")
public class MemberController {
    @Autowired
    private MemberService memberService;

    @Autowired
    private CouponFeignService couponFeignService;

    /**
     * 后台会员列表。
     *
     * <h3>2026-09-06 补的，此前这个控制器上没有任何查询入口</h3>
     * 和订单那次是同一个情况：MemberService.queryPage 一直存在，
     * 但控制器上的 /list 和 /info/{id} 被拿掉了，
     * 而同模块其它控制器（收货地址、等级、登录日志、成长值记录）
     * 都还留着完整的生成器 CRUD。
     *
     * <h3>筛选</h3>
     * {@code key}（用户名 / 昵称 / 手机号，模糊）、{@code levelId}、
     * {@code status}、{@code createTimeFrom} / {@code createTimeTo}。
     *
     * <h3>返回的是 MemberAdminVo，且手机号邮箱已脱敏</h3>
     * 不含 password / accessToken / socialUid，理由见 MemberAdminVo 的类注释。
     * 要看完整联系方式走 /info/{id}。
     */
    @GetMapping("/list")
    public R list(@RequestParam Map<String, Object> params) {
        return R.ok().put("page", memberService.queryPage(params));
    }

    /**
     * 后台会员详情。联系方式给全量（列表脱敏、详情不脱敏）。
     *
     * <p>查不到时返回业务错误而不是 {@code data: null} ——
     * 后者在前端会渲染成一个所有字段都空的详情框，
     * 看起来像"这个会员什么都没填"，而不是"这个会员不存在"。
     */
    @GetMapping("/info/{id}")
    public R info(@PathVariable("id") Long id) {
        MemberAdminVo member = memberService.getAdminDetail(id);
        if (member == null) {
            return R.error("会员不存在: " + id);
        }
        return R.ok().put("member", member);
    }

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