package com.mall.coupon.controller;

import com.mall.common.utils.R;
import com.mall.coupon.feign.MemberFeignService;
import com.mall.coupon.interceptor.CouponInterceptor;
import com.mall.coupon.to.UserInfoTo;
import com.mall.coupon.vo.MemberAddressVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台秒杀页的收货地址接口。<b>身份只从服务端会话取，不接受客户端自报。</b>
 *
 * <h3>这个控制器是为修一个已验证的越权漏洞而加的（2026-09-03）</h3>
 * 原来 {@code seckill.html} 直接调 mall-member 的生成器 CRUD：
 * <pre>
 * GET  /api/member/memberreceiveaddress/{memberId}/list
 * POST /api/member/memberreceiveaddress/save     // 请求体里带 memberId
 * </pre>
 * 两个问题叠在一起：
 * <ul>
 *   <li><b>身份由客户端自报</b> —— memberId 在 URL / 请求体里，改个数字就是别人；</li>
 *   <li><b>那批服务当时完全没有鉴权</b> —— mall-coupon / mall-product / mall-ware /
 *       mall-member 都只有一个「往 ThreadLocal 塞用户上下文然后无条件放行」的拦截器，
 *       没有任何一处校验。</li>
 * </ul>
 * 实测确认（本地集群、种子数据）：不带任何凭证请求
 * {@code /api/member/memberreceiveaddress/8000001/list} 返回 HTTP 200 和该会员的
 * {@code name / phone / detailAddress / province / city / region / postCode} —— 完整 PII，
 * 而且换个 id 就能遍历。
 *
 * <h3>为什么修在 mall-coupon 而不是 mall-member</h3>
 * 正确的做法是「memberId 从会话取」，但 <b>mall-member 没有引 mall-session-starter</b>，
 * 读不到前台会话，它根本无法知道请求者是谁。有会话的是 auth / cart / coupon / order，
 * 而 {@code seckill.html} 正是 mall-coupon 渲染的 —— 它既有会话、也已经有到 mall-member
 * 的 Feign。所以由它做这层代理：取会话里的 memberId，再转调下游。
 * <p>
 * 这也顺带把「前台唯一用到 {@code /api/} 前缀的地方」去掉了，于是
 * <b>{@code /api/**} 变成纯管理端流量</b>，网关就能对它整体加管理端 JWT 校验 ——
 * 一个前缀对应一个信任域，而不是两类流量混在一起。
 */
@RestController
@RequestMapping("coupon/seckill/address")
public class SeckillAddressController {

    private static final Logger log = LoggerFactory.getLogger(SeckillAddressController.class);

    @Autowired
    private MemberFeignService memberFeignService;

    /** 从会话取当前会员 id；未登录返回 null。 */
    private Long currentMemberId() {
        UserInfoTo userInfo = CouponInterceptor.threadLocal.get();
        return userInfo == null ? null : userInfo.getUserId();
    }

    /**
     * 我的收货地址列表。
     * <p>
     * 路径里<b>没有</b> memberId —— 这正是修法的核心：能查的永远只有自己的。
     */
    @GetMapping("/mine")
    public R mine() {
        Long memberId = currentMemberId();
        if (memberId == null) {
            return R.error(401, "请先登录");
        }
        return memberFeignService.getAddress(memberId);
    }

    /**
     * 新增我的收货地址。
     * <p>
     * 请求体里的 {@code memberId} 会被<b>无条件覆盖</b>成会话里的值 ——
     * 不是校验后拒绝，而是直接覆盖。理由：这个字段对客户端来说没有任何合法用途，
     * 校验再报错等于承认它是个「可以填但要填对」的字段，覆盖才是把它彻底变成
     * 服务端决定的东西。{@code id} 同理清空，避免用 save 覆盖别人已有的地址行。
     */
    @PostMapping("/mine")
    public R saveMine(@RequestBody MemberAddressVo address) {
        Long memberId = currentMemberId();
        if (memberId == null) {
            return R.error(401, "请先登录");
        }
        if (address == null) {
            return R.error("请求体不能为空");
        }
        if (address.getMemberId() != null && !address.getMemberId().equals(memberId)) {
            // 记下来但不报错：正常前端不会这么发，出现就说明有人在试。
            log.warn("收货地址请求体里的 memberId={} 与会话 memberId={} 不一致，已按会话覆盖",
                    address.getMemberId(), memberId);
        }
        address.setMemberId(memberId);
        address.setId(null);
        return memberFeignService.saveAddress(address);
    }
}
