package com.mall.coupon.feign;

import com.mall.common.utils.R;
import com.mall.coupon.vo.MemberAddressVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient("mall-member")
public interface MemberFeignService {


    @GetMapping("/member/memberreceiveaddress/{memberId}/list")
    R getAddress(@PathVariable("memberId") Long memberId);

    /**
     * 新增收货地址。
     * <p>
     * 【为什么由 mall-coupon 代理，而不是让前台页面直接调 mall-member】
     * 前台秒杀页原来直接 fetch('/api/member/memberreceiveaddress/save')，
     * 请求体里带着 memberId —— 也就是身份由客户端自报。加上这批服务当时完全没有鉴权，
     * 实测无凭证就能读写任意会员的地址（姓名/电话/详细地址）。
     * <p>
     * mall-member 没有引 mall-session-starter，读不到前台会话，没法自己判断
     * 「你是谁」。而 mall-coupon 有会话（seckill.html 就是它渲染的），
     * 所以由它取到 memberId 再转调 —— 身份来自服务端会话，客户端说什么都不算。
     */
    @PostMapping("/member/memberreceiveaddress/save")
    R saveAddress(@RequestBody MemberAddressVo address);
}
