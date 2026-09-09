package com.mall.coupon.controller;

import com.mall.coupon.service.CouponClaimService;
import com.mall.coupon.vo.PromotionCouponVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * 促销页（Thymeleaf 渲染），走 seckill.mall.com/promotion.html。
 *
 * <h3>为什么挂在 seckill.mall.com 而不是自己的域名</h3>
 * 网关里 {@code mall_seckill_route} 已经把 {@code Host=seckill.mall.com}
 * 指向 mall-coupon，所以这个页面<b>零网关改动</b>就能访问。
 * 给促销单独开一个 {@code promotion.mall.com} 需要：
 * 加一条网关路由（那个文件里的路由顺序被反复警告过，兜底规则
 * {@code Host=**.mall.com} 会接住任何没匹配上的域名并转给 mall-product，
 * 表现为"页面 404"而不是"路由没配"）、外加宿主机 hosts 文件条目。
 * 收益只是 URL 好看一点，不值。
 *
 * <h3>【代价】它继承了秒杀路由的限流</h3>
 * 那条路由上挂着 {@code replenishRate: 50 / burstCapacity: 100} 的全局限流器，
 * 所以促销页浏览和领券会<b>和秒杀抢同一份预算</b>。
 * 大促时要分开的话，就得付上面那个"单独域名"的代价。
 * 压测领券必须绕过网关直压 mall-coupon，否则量到的是限流器。
 *
 * <h3>不缓存，和秒杀页相反</h3>
 * {@code SeckillWebController} 给页面数据加了 2s/5s 的多级缓存，因为
 * "很多人围观、少数人抢"。促销页刻意<b>不缓存</b>：这个列表里最要紧的信息是
 * <b>还剩多少张</b>，而抢券期间 {@code receive_count} 是秒变的 ——
 * 缓存会让页面显示"还剩 8 张"而实际早已抢光，用户点了才知道。
 * 券的种类只有个位数、查询走 {@code idx_publish_window} 索引，
 * 直接查库的代价远小于显示错数字的代价。
 */
@Controller
public class PromotionWebController {

    @Autowired
    private CouponClaimService couponClaimService;

    @GetMapping("/promotion.html")
    public String promotionPage(Model model) {
        List<PromotionCouponVo> coupons = couponClaimService.promotions();
        model.addAttribute("coupons", coupons);
        return "promotion";
    }
}
