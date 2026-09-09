package com.mall.coupon.service;

import com.mall.coupon.dao.CouponDao;
import com.mall.coupon.dao.CouponHistoryDao;
import com.mall.coupon.entity.CouponEntity;
import com.mall.coupon.entity.CouponHistoryEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * 领券 / 用券 / 退券的原子操作。
 *
 * <h3>为什么单独一个 bean，而不是在 CouponClaimServiceImpl 上加 @Transactional</h3>
 * 两个原因，都和 Spring 的代理机制有关：
 * <ol>
 *   <li><b>{@code @Transactional} 靠代理生效，同一个类内部直接调用不经过代理</b>，
 *       注解等于没写。领券的"插记录 + 占名额"必须在一个事务里，
 *       所以它必须是一次<b>跨 bean</b> 的调用。</li>
 *   <li><b>回滚靠异常穿出被代理的方法</b>。捕获 {@link CouponSoldOutException}
 *       并把它翻译成错误码这件事，必须发生在事务边界<b>之外</b> ——
 *       在这个类里 try-catch 会让事务照常提交，"占名额失败"时插进去的
 *       领取记录就留下了，用户白得一张券。</li>
 * </ol>
 * mall-ware 的 {@code StockAtomicOps} 是同一个套路，那里的类注释里
 * 也记录了同样两条理由。
 */
@Component
public class CouponClaimTxOps {

    private static final Logger log = LoggerFactory.getLogger(CouponClaimTxOps.class);

    @Autowired
    private CouponDao couponDao;

    @Autowired
    private CouponHistoryDao couponHistoryDao;

    /**
     * 领一张券：插领取记录 + 占名额，两件事一起成功或一起回滚。
     *
     * <h3>【顺序是刻意的】先 INSERT 领取记录，后 UPDATE 占名额</h3>
     * 两者在同一事务里，正确性上顺序无所谓 —— 任一失败都全回滚。
     * 但顺序决定<b>热点行锁被持有多久</b>，也就是这个接口的吞吐上限：
     * <p>
     * {@code UPDATE sms_coupon WHERE id = ?} 会对<b>同一行</b>加排他锁，
     * 而且锁一直持有到事务提交。如果它排在前面，那么后面 INSERT 的整个耗时
     * （唯一索引查找 + 写 undo/redo）都在持锁期间 —— 所有并发领同一张券的请求
     * 会串行地排队等这把锁，队列长度就是并发数。
     * 把 UPDATE 放最后，持锁窗口压缩到只剩一次自增。
     * <p>
     * 还有一个附带好处：<b>重复点击</b>（最常见的失败）会在 INSERT 那步
     * 因唯一索引冲突立刻失败，<b>根本不会去碰热点行</b>。
     * 反过来的话，每次重复点击都要先抢一次热点行锁再回滚。
     *
     * <h3>【限领是两个机制合起来才成立的，别只依赖其中一个】</h3>
     * 2026-09-08 对集群真库做过并发实测，两边各去掉一个都会超领：
     * <ul>
     *   <li><b>唯一索引 {@code uk_member_coupon_seq}</b> 保证一个 seq 槽位
     *       只能有一个赢家 —— 并发请求算出同一个 seq 时，只有一个能插进去，
     *       其余拿到 {@code DuplicateKeyException}。</li>
     *   <li><b>调用方的 {@code already >= perLimit} 检查 + 冲突后重新计数再试</b>
     *       才是给 seq 定上限的那一半。见 {@code CouponClaimServiceImpl.receive}。</li>
     * </ul>
     * <b>实测：</b>只留唯一索引、把 perLimit 检查去掉，30 并发抢一张
     * {@code per_limit = 2} 的券，结果是 <b>3 张</b>（{@code receive_seq} = 1,2,3）——
     * 因为读到 count=2 的线程会算出 seq=3，而唯一索引对 seq=3 毫无意见。
     * 补回检查之后同样条件下是 <b>2 张</b>（seq = 1,2）。
     * <p>
     * 所以这个参数<b>不是</b>"算错也没关系"的。它必须由调用方在
     * 检查过 perLimit 之后算出，而且冲突重试时必须<b>重新计数</b>，
     * 不能简单地 {@code seq + 1}。
     *
     * @param seq 本人领取序号，必须落在 1..per_limit 内，由调用方保证
     * @throws org.springframework.dao.DuplicateKeyException 这个 seq 已被占（并发或重复点击）
     * @throws CouponSoldOutException                       名额已满
     */
    @Transactional(rollbackFor = Exception.class)
    public Long claim(CouponEntity coupon, Long memberId, String nickName, int seq) {
        CouponHistoryEntity history = new CouponHistoryEntity();
        history.setCouponId(coupon.getId());
        history.setMemberId(memberId);
        history.setMemberNickName(nickName);
        history.setReceiveSeq(seq);
        history.setGetType(1); // 1 = 主动领取
        history.setCreateTime(new Date());
        // 有效期在这一刻冻结。不能等用券时去读 sms_coupon.end_time ——
        // 后台改已发布券的 end_time 会让已发出的券追溯失效。
        history.setExpireTime(coupon.getEndTime());
        history.setUseType(0); // 0 = 未使用
        couponHistoryDao.insert(history);

        if (couponDao.tryReserveOne(coupon.getId()) == 0) {
            // 抛出去让事务回滚上面的 INSERT。在这里 catch 掉就是超发。
            throw new CouponSoldOutException(coupon.getId());
        }
        return history.getId();
    }

    /**
     * 用券：把券标记为已使用并绑定订单号。
     *
     * <h3>【顺序是刻意的】先改券状态（权威），后累加统计</h3>
     * 两者同事务，正常情况顺序无所谓。但顺序决定"事务机制本身失效时"
     * （进程被 kill、连接断在提交途中）留下的痕迹方向：
     * <ul>
     *   <li>先改状态 -> 可能留下"券已用、use_count 少 1"，
     *       是<b>统计漂移</b>，不影响任何业务判断</li>
     *   <li>先累加   -> 可能留下"use_count 多 1、券还可用"，
     *       券会被再用一次 —— 业务错误</li>
     * </ul>
     * 两种都不该发生，但要选一个更安全的失败方向。
     * StockAtomicOps 的类注释里做过同样的取舍。
     *
     * @return true = 本次真的用掉了这张券；
     *         false = 券不存在 / 不属于此人 / 已被用掉。<b>调用方必须让下单失败</b>。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean use(Long historyId, Long memberId, Long couponId, String orderSn, Long orderId) {
        if (couponHistoryDao.markUsed(historyId, memberId, orderSn, orderId, new Date()) == 0) {
            return false;
        }
        couponDao.incrementUseCount(couponId);
        return true;
    }

    /**
     * 退券：订单在用券之后落库失败时的补偿。
     * <p>
     * 幂等：{@code markUnusedByOrder} 的 WHERE 里带 {@code order_sn} 和
     * {@code use_type = 1}，重复调用第二次会返回 0 并被这里安全忽略。
     * 这一点很重要 —— 补偿路径天生会被重投。
     *
     * @return true = 这次退回了；false = 已经不是这笔订单占着（重复补偿）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean release(Long historyId, Long couponId, String orderSn) {
        if (couponHistoryDao.markUnusedByOrder(historyId, orderSn) == 0) {
            log.info("退券跳过：券已不是这笔订单占用（重复补偿或券已被用于别处）historyId={} orderSn={}",
                    historyId, orderSn);
            return false;
        }
        couponDao.decrementUseCount(couponId);
        log.info("退券完成 historyId={} couponId={} orderSn={}", historyId, couponId, orderSn);
        return true;
    }
}
