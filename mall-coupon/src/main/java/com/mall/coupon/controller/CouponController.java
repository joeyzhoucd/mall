package com.mall.coupon.controller;

import com.mall.common.utils.R;
import com.mall.coupon.entity.CouponEntity;
import com.mall.coupon.service.CouponService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import com.mall.common.utils.PageUtils;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import java.util.Map;




@RestController
@RequestMapping("coupon/coupon")
public class CouponController {

    private static final Logger log = LoggerFactory.getLogger(CouponController.class);

    @Autowired
    private CouponService couponService;

    @RequestMapping("/member/list")
    public R membercoupons() {
        CouponEntity couponEntity = new CouponEntity();
        couponEntity.setCouponName("Full 100 off 10");
        return R.ok().put("coupons", Arrays.asList(couponEntity));
    }


    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "This is a placeholder method");
    }

    /**
     * 优惠券分页列表。后台券管理页和「给 SKU 绑券」的下拉框都用它。
     */
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params) {
        PageUtils page = couponService.queryPage(params);
        return R.ok().put("page", page);
    }

    /** 单张券详情，后台编辑表单用。 */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id) {
        CouponEntity coupon = couponService.getById(id);
        if (coupon == null) {
            return R.error("优惠券不存在");
        }
        return R.ok().put("coupon", coupon);
    }

    /**
     * 新建优惠券。
     *
     * <h3>三个计数器由服务端强制归零，不采信请求体</h3>
     * {@code receive_count} / {@code use_count} 是<b>运行时计数</b>，
     * 不是可编辑的属性。允许创建时指定它们，等于允许凭空把
     * "已领 0 张"写成"已领 9999 张"（把券直接作废），
     * 或者反过来把上限撑开。
     * <p>
     * {@code num} 这一列是生成器留下的历史字段，业务上不用它 ——
     * 领取上限看 {@code publish_count}。这里跟着 publish_count 一起写，
     * 免得两处数字不一致时不知道该信哪个。
     */
    @PostMapping("/save")
    public R save(@RequestBody CouponEntity coupon) {
        String invalid = validate(coupon);
        if (invalid != null) {
            return R.error(invalid);
        }
        coupon.setId(null);
        coupon.setReceiveCount(0);
        coupon.setUseCount(0);
        coupon.setNum(coupon.getPublishCount());
        couponService.save(coupon);
        log.info("后台新建优惠券 id={} name={} publishCount={} perLimit={}",
                coupon.getId(), coupon.getCouponName(), coupon.getPublishCount(), coupon.getPerLimit());
        return R.ok().put("id", coupon.getId());
    }

    /**
     * 修改优惠券。
     *
     * <h3>【关键】必须把计数器置空，否则编辑一次券就可能造成超发</h3>
     * MyBatis-Plus 的 {@code updateById} 会把实体里<b>所有非空字段整行写回</b>。
     * 后台编辑表单是「先 GET 详情、改几个字段、再 POST 回来」，
     * 所以请求体里带着的 {@code receive_count} 是<b>打开表单那一刻</b>的值。
     * 如果原样写回：
     * <pre>
     *   运营 10:00 打开表单        receive_count = 40
     *   10:00-10:05 用户领了 60 张  receive_count = 100（已达上限 100）
     *   运营 10:05 点保存          receive_count 被写回 40
     *                              -> 上限凭空多出 60 张，直接超发
     * </pre>
     * 置成 null 之后 MyBatis-Plus 会跳过这两列，计数器只由
     * {@code CouponDao.tryReserveOne} 的条件更新推进。
     * <p>
     * mall-ware 的 {@code WareOrderTaskDetailDao} 注释里记录过同一个坑的另一个版本：
     * 用 {@code updateById} 整行写回把已经推进的 {@code lock_status} 改回 LOCKED，
     * 导致库存被释放两次。<b>同一个机制，两次事故。</b>
     * 结论是通用的：<b>凡是被并发推进的列，永远不要走整行写回。</b>
     */
    @PostMapping("/update")
    public R update(@RequestBody CouponEntity coupon) {
        if (coupon.getId() == null) {
            return R.error("缺少 id");
        }
        String invalid = validate(coupon);
        if (invalid != null) {
            return R.error(invalid);
        }
        CouponEntity existing = couponService.getById(coupon.getId());
        if (existing == null) {
            return R.error("优惠券不存在");
        }
        // 不能把发行量改到比已领量还小 —— 那会让 receive_count >= publish_count
        // 永远成立，券立刻变成"已领完"，而已经发出去的券又收不回来。
        // 允许改大（追加发行）。
        if (coupon.getPublishCount() != null
                && coupon.getPublishCount() < existing.getReceiveCount()) {
            return R.error("发行量不能小于已领取量 " + existing.getReceiveCount());
        }
        coupon.setReceiveCount(null);
        coupon.setUseCount(null);
        coupon.setNum(coupon.getPublishCount());
        couponService.updateById(coupon);
        log.info("后台修改优惠券 id={} publishCount={}", coupon.getId(), coupon.getPublishCount());
        return R.ok();
    }

    /**
     * 删除优惠券。
     *
     * <h3>已经有人领过的券不能删</h3>
     * {@code sms_coupon_history} 里的行靠 {@code coupon_id} 关联券面信息
     * （名称、面额、门槛都在 {@code sms_coupon} 上，领取记录里不存快照）。
     * 删掉券之后，"我的优惠券"里那些行 join 不上，会直接<b>从列表里消失</b> ——
     * 用户手里的券凭空不见了，而且没有任何报错。
     * <p>
     * 想让一张券停止发放，正确做法是把 {@code publish} 改成 0（下架），
     * 已发出的券仍然可用。这是"停止发放"和"抹掉历史"的区别。
     */
    @PostMapping("/delete")
    public R delete(@RequestBody Long[] ids) {
        if (ids == null || ids.length == 0) {
            return R.error("没有指定要删除的优惠券");
        }
        List<Long> idList = Arrays.asList(ids);
        List<CouponEntity> coupons = couponService.listByIds(idList);
        for (CouponEntity coupon : coupons) {
            if (coupon.getReceiveCount() != null && coupon.getReceiveCount() > 0) {
                return R.error("优惠券「" + coupon.getCouponName() + "」已被领取 "
                        + coupon.getReceiveCount() + " 张，不能删除。"
                        + "要停止发放请把发布状态改为未发布。");
            }
        }
        couponService.removeByIds(idList);
        log.info("后台删除优惠券 ids={}", idList);
        return R.ok();
    }

    /**
     * 表单校验。
     *
     * <p>放在服务端而不是只靠前端：这些接口走
     * {@code /api/coupon/**} 网关路由，任何拿到管理员令牌的人都能直接构造请求。
     * 前端校验只是提前给提示，不是防线。
     */
    private String validate(CouponEntity coupon) {
        if (coupon.getCouponName() == null || coupon.getCouponName().isBlank()) {
            return "券名不能为空";
        }
        if (coupon.getAmount() == null || coupon.getAmount().signum() <= 0) {
            return "面额必须大于 0";
        }
        if (coupon.getMinPoint() != null && coupon.getMinPoint().signum() < 0) {
            return "使用门槛不能为负";
        }
        if (coupon.getPublishCount() == null || coupon.getPublishCount() <= 0) {
            return "发行量必须大于 0";
        }
        if (coupon.getPerLimit() == null || coupon.getPerLimit() <= 0) {
            return "每人限领张数必须大于 0";
        }
        if (coupon.getStartTime() != null && coupon.getEndTime() != null
                && coupon.getEndTime().before(coupon.getStartTime())) {
            return "券的有效期结束时间不能早于开始时间";
        }
        if (coupon.getEnableStartTime() != null && coupon.getEnableEndTime() != null
                && coupon.getEnableEndTime().before(coupon.getEnableStartTime())) {
            return "领取截止时间不能早于领取开始时间";
        }
        // 领到手却已经过期的券没有意义，而且前台会显示成"已过期"让人以为是 bug。
        if (coupon.getEndTime() != null && coupon.getEnableStartTime() != null
                && coupon.getEndTime().before(coupon.getEnableStartTime())) {
            return "券的有效期在领取开始之前就结束了，领到手即过期";
        }
        // member_level 目前无法校验（ums_member_level 是空表、也没有查等级的接口），
        // 领取接口会一律拒绝这类券。允许存但要明确告知，别让运营建了个没人能领的活动。
        if (coupon.getMemberLevel() != null && coupon.getMemberLevel() != 0) {
            return "会员等级限制暂未实现：等级体系还没建立（ums_member_level 为空），"
                    + "带等级限制的券会被领取接口一律拒绝。请填 0（不限等级）。";
        }
        // 适用范围同理：指定分类/指定商品需要订单里的 spu_id 和 category_id，
        // 而 oms_order_item 那两列目前从来不写。
        if (coupon.getUseType() != null && coupon.getUseType() != 0) {
            return "指定分类/指定商品的券暂未实现：结算时拿不到订单商品的 spu 和分类"
                    + "（oms_order_item.spu_id / category_id 未写入），这类券无法抵扣。"
                    + "请填 0（全场通用）。";
        }
        return null;
    }
}
