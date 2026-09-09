package com.mall.common.constant;

public enum ErrorCode {
    ORDER_NOT_FOUND(20001, "订单不存在"),
    PAY_SIGN_INVALID(20002, "签名校验失败"),
    ORDER_STATUS_TRANSITION_ILLEGAL(20003, "illegal order status transition"),
    STOCK_NOT_ENOUGH(21001, "库存不足"),
    SECKILL_SOLD_OUT(22001, "已经卖光了，下次再来"),
    SECKILL_ALREADY_GRABBED(22002, "你已经抢到过了"),
    SECKILL_NOT_ACTIVE(22003, "活动还没开始或已下线"),
    SECKILL_MQ_FAILED(22004, "抢购失败，请重试"),
    SECKILL_MESSAGE_INVALID(22005, "抢购记录不存在或状态不对"),
    SECKILL_FORBIDDEN(22006, "无权限"),
    SECKILL_SYSTEM_ERROR(22007, "系统繁忙，请重试"),

    // ---------------------------------------------------------------------
    // 优惠券领取/使用（23xxx）
    // ---------------------------------------------------------------------
    // 【为什么每种失败都要有自己的码，不能都返回"领取失败"】
    // 这些码同时是压测的判据。领券压测要断言的是「并发 200 抢 10 张 =
    // 恰好 10 个 23000 + 190 个 23002」，如果超发了它表现为 11 个成功 ——
    // 而把 RECEIVED_OUT 和 PER_LIMIT_EXCEEDED 混成一个码，
    // 就分不清"券发完了"和"这个人领满了"，也就验证不了限领。
    // 秒杀那批码当初也是为了同一个原因才拆开的。
    COUPON_RECEIVE_OK(23000, "领取成功"),
    COUPON_NOT_FOUND(23001, "优惠券不存在"),
    COUPON_SOLD_OUT(23002, "该优惠券已被领完"),
    COUPON_PER_LIMIT_EXCEEDED(23003, "你已领取过该优惠券"),
    COUPON_NOT_PUBLISHED(23004, "该优惠券未发布"),
    COUPON_RECEIVE_WINDOW_CLOSED(23005, "不在领取时间内"),
    COUPON_MEMBER_LEVEL_NOT_MATCH(23006, "会员等级不满足领取条件"),
    COUPON_UNAUTHENTICATED(23007, "请先登录"),
    // 以下三个是「用券」阶段的，由 mall-order 通过 Feign 触发
    COUPON_NOT_OWNED(23010, "优惠券不属于当前会员"),
    COUPON_ALREADY_USED(23011, "优惠券已被使用"),
    COUPON_EXPIRED(23012, "优惠券已过期"),
    COUPON_THRESHOLD_NOT_MET(23013, "订单金额未达到使用门槛"),
    COUPON_SCOPE_NOT_MATCH(23014, "订单中没有该券适用的商品"),
    REQUEST_FAILED(10000, "请求失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}

