package com.mall.coupon.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Map;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;

import com.mall.coupon.dao.CouponHistoryDao;
import com.mall.coupon.entity.CouponHistoryEntity;
import com.mall.coupon.service.CouponHistoryService;


@Service("couponHistoryService")
public class CouponHistoryServiceImpl extends ServiceImpl<CouponHistoryDao, CouponHistoryEntity> implements CouponHistoryService {

    /**
     * 后台的领券记录分页。
     *
     * <h3>为什么要补筛选</h3>
     * 生成器给的是空 {@code QueryWrapper} —— 只能一页页翻。
     * {@code sms_coupon} 只有个位数行，翻得起；但 {@code sms_coupon_history}
     * <b>每领一张券就多一行</b>，一次促销就是几万行。
     * 而后台看这张表的实际用途是「客服要查某个人的某张券去哪了」，
     * 没有筛选等于这个页面做了也没用。
     *
     * <h3>ORDER BY 本来就有，别删</h3>
     * {@code orderByDesc("id")} 是分页正确性的一部分，不是排序偏好：
     * MySQL 不保证无序查询的行顺序，而分页的每一页是独立查询 ——
     * 没有全序时行会在页与页之间重复或漏掉。
     * 这个库在 spuinfo 上实测过：10004 行时第 1、2 页重复 8 行。
     * id 是主键、天然唯一，单独按它排就已经是全序。
     */
    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CouponHistoryEntity> page = this.page(
                new Query<CouponHistoryEntity>().getPage(params),
                buildQueryWrapper(params)
        );

        return new PageUtils(page);
    }

    /**
     * 条件拼装单独抽出来，为的是<b>可测</b>：{@code queryPage} 要连数据库，
     * 而这一段是纯逻辑。
     *
     * <p>【这个抽取不是可有可无的】{@code OrderQueryPageTest} 的第一版自己复刻了
     * 一份拼装逻辑来测，结果把生产代码里的 {@code orderByDesc} 整行删掉，
     * 8 条测试<b>全部照常通过</b> —— 因为它们测的是复刻的那一份。
     * 一个永远通过的测试比没有测试更糟。所以这里也让测试调这个方法本身。
     */
    QueryWrapper<CouponHistoryEntity> buildQueryWrapper(Map<String, Object> params) {
        QueryWrapper<CouponHistoryEntity> wrapper = new QueryWrapper<>();

        Long couponId = parseLong(params.get("couponId"));
        if (couponId != null) {
            wrapper.eq("coupon_id", couponId);
        }

        Long memberId = parseLong(params.get("memberId"));
        if (memberId != null) {
            wrapper.eq("member_id", memberId);
        }

        // 使用状态：0 未使用 / 1 已使用 / 2 已过期。
        // 【注意】2（已过期）在库里【不存在】—— 过期是读的时候按 expire_time 算的，
        // 没有定时任务去把 use_type 改成 2。所以这里按 use_type 筛 2 会返回空。
        // 前端的"已过期"页签必须用下面的 expiredBefore 而不是 status=2。
        Integer status = parseInt(params.get("status"));
        if (status != null && status != 2) {
            wrapper.eq("use_type", status);
        }

        // 已过期 = 未使用 且 expire_time 已过。给前端的"已过期"页签用。
        String expiredBefore = trimmed(params.get("expiredBefore"));
        if (expiredBefore != null) {
            wrapper.eq("use_type", 0).isNotNull("expire_time").lt("expire_time", expiredBefore);
        }

        // 订单号：精确匹配。它是给人报出来的东西（客服问「您的订单号是多少」），
        // 用 like 会让人输错一位也能查到别人的单子。
        String orderSn = trimmed(params.get("orderSn"));
        if (orderSn != null) {
            wrapper.eq("order_sn", orderSn);
        }

        return wrapper.orderByDesc("id");
    }

    private static String trimmed(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer parseInt(Object raw) {
        String s = trimmed(raw);
        if (s == null) return null;
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            // 解析不了当没传。抛异常的话前端传个空串就变成 500，而这只是个筛选条件。
            return null;
        }
    }

    private static Long parseLong(Object raw) {
        String s = trimmed(raw);
        if (s == null) return null;
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
