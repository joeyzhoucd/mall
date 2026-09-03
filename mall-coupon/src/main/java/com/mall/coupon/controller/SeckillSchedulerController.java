package com.mall.coupon.controller;

import com.mall.common.utils.R;
import com.mall.coupon.service.SeckillSchedulerService;
import com.mall.coupon.vo.SeckillSchedulerVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台「给某个 SKU 一键配置秒杀」。
 *
 * <h3>这个接口是接口审计里最后一个缺口（2026-09-03 补上）</h3>
 * {@code tools/audit-endpoints.js} 长期报着一条：后台 SKU 页的
 * {@code product/sku.vue} 在调 {@code /seckill/scheduler/save}，而后端压根没有。
 * mall-gateway 的配置里也写着这件事，并且<b>刻意没有加对应路由</b> ——
 * 「加一条指向不存在接口的路由只是把 404 从网关挪到服务」。
 * <p>
 * 补的时候没有按网关注释里设想的那样新开 {@code /api/seckill/**} 路由，
 * 而是把接口放在<b>已有的 {@code coupon/seckill} 前缀下</b>，前端改调
 * {@code /coupon/seckill/scheduler/save}。这样 {@code coupon_route}
 * （{@code Path=/api/coupon/**}）现成就能覆盖，网关一行都不用改 —— 少一个活动部件，
 * URL 也和其它秒杀接口保持一致。
 *
 * <h3>和 {@link SeckillSkuRelationController} 的分工</h3>
 * 那个是 renren 生成器产出的单表 CRUD，按主键改一条关系；
 * 这个是跨「场次 + 关系」两张表的编排操作，对应后台的一个业务动作。
 * 两者对「活动进行中不能改库存」这条规则的判断是一致的，错误信息也刻意用同一段话。
 */
@RestController
@RequestMapping("coupon/seckill/scheduler")
public class SeckillSchedulerController {

    private static final Logger log = LoggerFactory.getLogger(SeckillSchedulerController.class);

    @Autowired
    private SeckillSchedulerService seckillSchedulerService;

    /**
     * 配置（或更新）一个 SKU 的秒杀。
     * <p>
     * 返回体里带 {@code relationId}，因为<b>保存不等于上线</b>：
     * 真实库存在 Redis，要再调一次 {@code POST /coupon/seckill/activate/{relationId}}
     * 才会生效。把 id 返回去，前端才有可能把「激活」这一步接上。
     */
    @PostMapping("/save")
    public R save(@RequestBody SeckillSchedulerVo vo) {
        try {
            Long relationId = seckillSchedulerService.save(vo);
            return R.ok()
                    .put("relationId", relationId)
                    .put("activated", false)
                    .put("msg", "秒杀配置已保存。注意：还没有上线，需要在活动开始前调用 "
                            + "POST /coupon/seckill/activate/" + relationId + " 激活。");
        } catch (IllegalArgumentException e) {
            // 参数问题返回业务错误而不是 500：这些都是管理员在界面上填错了，
            // 不是服务故障，不该进 ERROR 日志、也不该污染错误率指标。
            log.info("秒杀配置被拒绝: {}", e.getMessage());
            return R.error(e.getMessage());
        }
    }
}
