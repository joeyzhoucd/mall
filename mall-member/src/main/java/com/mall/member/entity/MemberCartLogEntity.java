package com.mall.member.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 购物车行为日志 —— 推荐系统的加购信号。
 *
 * <p>建表见 {@code mall-deploy/data-seed/migration-2026-09-23-indexes-and-cart-log.sql}。
 *
 * <h3>为什么这张表在 mall_ums 而写入方是 mall-cart</h3>
 * 购物车服务是 <b>Redis-only</b> 的，它的 pom 里<b>刻意排除了</b>
 * mybatis / mysql-connector（见 mall-cart/pom.xml 的 exclusions）。
 * 为了埋点给它加回一个数据源，等于推翻那个设计选择。
 * 所以写入放在拥有 {@code mall_ums} 库的 mall-member，mall-cart 走 Feign 投递。
 *
 * <h3>为什么冗余 spuId</h3>
 * 共现统计必须在 SPU 粒度做：SKU 是颜色/版本变体，在 SKU 粒度算会把
 * 「同一商品的黑色和白色」当成共现对 —— 和相似商品里排除同 spuId 是同一个道理。
 * 统计时再 join {@code pms_sku_info} 回表查，几十万行的 join 不划算。
 */
@Data
@TableName("ums_member_cart_log")
public class MemberCartLogEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 加购 */
    public static final int ACTION_ADD = 1;
    /** 改数量 */
    public static final int ACTION_CHANGE_COUNT = 2;
    /** 删除 */
    public static final int ACTION_DELETE = 3;
    /** 选中状态变更 */
    public static final int ACTION_CHECK = 4;

    @TableId
    private Long id;

    /** 会员 id；未登录的临时购物车写 0 */
    private Long memberId;

    private Long skuId;

    private Long spuId;

    /** 见上面的 ACTION_* 常量 */
    private Integer action;

    /** 动作之后的数量 */
    private Integer quantity;

    private Date createTime;
}
