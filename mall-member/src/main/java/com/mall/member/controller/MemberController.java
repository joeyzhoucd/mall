package com.mall.member.controller;

import com.mall.common.utils.R;
import com.mall.member.entity.MemberEntity;
import com.mall.member.feign.CouponFeignService;
import com.mall.member.service.MemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ä¼šå‘˜æŽ§åˆ¶å™¨
 * ä¿ç•™åŸºç¡€ç»“æž„ï¼Œåˆ é™¤å‰ç«¯æœªä½¿ç”¨çš„æ–¹æ³•
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
     * æµ‹è¯•æŽ¥å£ - èŽ·å–ä¼šå‘˜ä¼˜æƒ åˆ¸ä¿¡æ¯
     */
    @RequestMapping("/coupons")
    public R test() {
        MemberEntity memberEntity = new MemberEntity();
        memberEntity.setNickname("å¼ ä¸‰");
        R membercoupons = couponFeignService.memberCoupons();
        return R.ok().put("member", memberEntity).put("coupons", membercoupons.get("coupons"));
    }

    /**
     * é¢„ç•™æŽ¥å£ - ä¼šå‘˜åŠŸèƒ½å¾…å¼€å‘
     */
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "ä¼šå‘˜åŠŸèƒ½å¾…å¼€å‘");
    }
}
