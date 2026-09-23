package com.mall.member.controller;

import com.mall.common.utils.R;
import com.mall.member.dao.MemberCartLogDao;
import com.mall.member.entity.MemberCartLogEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 * 购物车行为日志的写入端点，给 mall-cart 通过 Feign 批量投递。
 *
 * <h3>为什么写入方不是 mall-cart 自己</h3>
 * 购物车服务是 Redis-only 的，pom 里<b>刻意排除了</b> mybatis / mysql-connector。
 * 为了埋点给它加回数据源等于推翻那个设计。所以由拥有 {@code mall_ums} 的本服务落库。
 *
 * <h3>【已知缺口】这个端点从公网可达</h3>
 * 网关有 {@code Path=/api/member/**} 的路由，所以
 * {@code POST /api/member/cartlog/batch} 外网打得到，任何人都能伪造行为数据。
 * <b>这不是本端点特有的问题</b>：项目里所有给 Feign 用的内部端点
 * （如 mall-product 的 CartFeignController）都一样暴露，是网关路由粒度的系统性缺口。
 * 已记在 PLAN 的「已知缺陷」里，要统一解决而不是在这里单点打补丁。
 * <p>
 * 当下的风险可接受：这张表只用于离线统计，不参与任何业务判断；
 * 一旦它开始影响钱或权限，必须先把内部端点的访问控制做掉。
 */
@Slf4j
@RestController
@RequestMapping("/member/cartlog")
public class MemberCartLogController {

    /**
     * 单批上限。超过这个数一条 SQL 可能撞上 max_allowed_packet，
     * 而那会让【整批】失败 —— 埋点丢一批比丢一条糟糕得多。
     */
    private static final int MAX_BATCH = 500;

    @Autowired
    private MemberCartLogDao cartLogDao;

    @PostMapping("/batch")
    public R batch(@RequestBody List<MemberCartLogEntity> list) {
        if (list == null || list.isEmpty()) {
            return R.ok().put("saved", 0);
        }
        if (list.size() > MAX_BATCH) {
            log.warn("购物车埋点单批 {} 条超过上限 {}，拒绝", list.size(), MAX_BATCH);
            return R.error("批量过大");
        }
        Date now = new Date();
        for (MemberCartLogEntity e : list) {
            // id 由数据库生成。调用方传了也忽略 —— 这是个对外可达的端点，
            // 不能让调用方指定主键。
            e.setId(null);
            if (e.getCreateTime() == null) {
                e.setCreateTime(now);
            }
        }
        int n = cartLogDao.insertBatch(list);
        return R.ok().put("saved", n);
    }
}
