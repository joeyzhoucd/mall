package com.mall.coupon.service;

/**
 * "名额已被占满"。
 *
 * <h3>为什么用异常而不是返回值</h3>
 * 领券的两步（插领取记录 + 占名额）必须在<b>同一个事务</b>里，
 * 而 Spring 的 {@code @Transactional} 回滚是靠"异常穿出被代理的方法"触发的。
 * 第二步条件更新返回 0 时如果只是 {@code return false}，事务会<b>正常提交</b> ——
 * 第一步插进去的领取记录就留下了，用户手里多出一张<b>券本体没有计数</b>的券。
 * 那正是超发，而且账面上看不出来（receive_count 是对的，history 多一行）。
 * <p>
 * 所以必须抛。捕获点在 {@code CouponClaimServiceImpl}，也就是<b>事务边界之外</b>——
 * 在 {@code CouponClaimTxOps} 内部 try-catch 会让异常穿不出代理，事务照样提交，
 * 等于这个异常白抛了。这也是为什么原子操作要单独一个 bean
 * （同一个类里的内部调用不经过代理，注解等于没写）。
 */
public class CouponSoldOutException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Long couponId;

    public CouponSoldOutException(Long couponId) {
        // 不带堆栈：这是并发下的正常结果（券抢完了），不是缺陷。
        // 高并发领券时每秒可能抛出成百上千个，填堆栈是纯开销。
        super("优惠券已被领完: couponId=" + couponId, null, false, false);
        this.couponId = couponId;
    }

    public Long getCouponId() {
        return couponId;
    }
}
