package com.mall.cart.to;

import lombok.Data;

import java.util.Date;

/**
 * 一条购物车行为埋点。字段和 mall_ums.ums_member_cart_log 一一对应。
 */
@Data
public class CartLogTo {

    public static final int ACTION_ADD = 1;
    public static final int ACTION_CHANGE_COUNT = 2;
    public static final int ACTION_DELETE = 3;
    public static final int ACTION_CHECK = 4;

    /** 未登录的临时购物车写 0 —— 临时 userKey 是个 UUID，塞不进 bigint，
     *  而把它哈希成数字会制造出一批永远关联不上真人的假会员 id。 */
    private Long memberId;
    private Long skuId;
    private Long spuId;
    private Integer action;
    private Integer quantity;
    private Date createTime;
}
