package com.mall.coupon.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.coupon.entity.CouponHistoryEntity;
import com.mall.coupon.vo.MemberCouponVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;


/**
 * 领取记录的 DAO。
 *
 * <h3>为什么这里全是条件更新，没有一个"先查再写"</h3>
 * 券的正确性有两个维度（总量不超发、单人不超领），两者都无法靠应用层的
 * 「读-判断-写」保证 —— 读和写之间有窗口，而且服务是多副本部署，
 * JVM 锁跨不了进程。所以每个改状态的操作都把判断条件写进 {@code WHERE}，
 * 用返回的 affected rows 区分"我做成了"和"别人已经做过了"。
 * <p>
 * 这是 mall-ware {@code WareOrderTaskDetailDao.casLockStatus} 的同一个套路。
 */
@Mapper
public interface CouponHistoryDao extends BaseMapper<CouponHistoryEntity> {

	/**
	 * 这个会员已经领了这张券几张。
	 *
	 * <h3>这个 COUNT 是限领机制的一半，不是可有可无的辅助查询</h3>
	 * 直觉上会觉得"读-判断-写"有窗口所以不可靠，真正的守卫是唯一索引 ——
	 * <b>实测证明这个直觉是错的，两个机制缺一不可</b>
	 * （2026-09-08 对集群真库做的并发实验）：
	 * <ul>
	 *   <li>唯一索引 {@code uk_member_coupon_seq (member_id, coupon_id, receive_seq)}
	 *       只保证<b>一个 seq 槽位只有一个赢家</b>。它对"seq 该不该涨到 3"没有意见。</li>
	 *   <li>去掉调用方的 {@code already >= perLimit} 检查，30 并发抢一张
	 *       {@code per_limit = 2} 的券，结果是 <b>3 张</b>（seq = 1,2,3）。</li>
	 *   <li>补回检查后，同样条件下是 <b>2 张</b>（seq = 1,2），21 个 DUP、7 个超限拒绝。</li>
	 * </ul>
	 * 为什么两者合起来就够：要插进 {@code seq = perLimit + 1}，
	 * 必须先读到 {@code count = perLimit}，而那会被检查直接拒掉；
	 * 而唯一索引保证 seq 不重复、行数不会超过最大 seq。
	 * 关键是<b>冲突后必须重新计数再试</b>（不能 {@code seq + 1} 接着插），
	 * 否则就绕过了这个上限。
	 */
	int countByMemberAndCoupon(@Param("memberId") Long memberId, @Param("couponId") Long couponId);

	/**
	 * 用券：把券从"未使用"推进到"已使用"，并写上订单号。
	 * <p>
	 * 三个条件全部写进 WHERE，缺一个就是一个洞：
	 * <ul>
	 *   <li>{@code id = ?} —— 目标券</li>
	 *   <li>{@code member_id = ?} —— <b>越权保护</b>。少了这条，
	 *       任何人只要猜到别人的 couponHistoryId 就能用别人的券。
	 *       mall-order 传进来的 memberId 来自网关解析的可信身份，不是客户端参数。</li>
	 *   <li>{@code use_type = 0} —— <b>重复使用保护</b>。少了这条，
	 *       同一张券可以被两笔订单同时用掉（并发结算），或者被重复提交用两次。</li>
	 * </ul>
	 *
	 * @return 1 = 本次真的把券用掉了；0 = 券不存在 / 不属于此人 / 已被用掉。
	 *         调用方<b>必须</b>把 0 当作失败让下单失败，不能忽略。
	 */
	int markUsed(@Param("id") Long id,
	             @Param("memberId") Long memberId,
	             @Param("orderSn") String orderSn,
	             @Param("orderId") Long orderId,
	             @Param("now") Date now);

	/**
	 * 补偿：把券退回"未使用"。订单在用券之后落库失败时调用。
	 * <p>
	 * <b>{@code order_sn} 必须出现在 WHERE 里</b>，只按 id 退会退错券：
	 * 假如补偿消息重投，而这张券期间已经被会员用在了另一笔订单上，
	 * 只按 id 退会把那笔<b>有效</b>订单的券也退掉 —— 变成一张券用两次。
	 * 带上 order_sn 就把补偿限定在"确实是这笔订单占用的那张"。
	 *
	 * @return 1 = 退回成功；0 = 这张券已经不是被这笔订单占着（重复补偿，安全忽略）
	 */
	int markUnusedByOrder(@Param("id") Long id, @Param("orderSn") String orderSn);

	/**
	 * "我的优惠券"列表。连 sms_coupon 取券面信息（名称/面额/门槛/适用范围）。
	 * <p>
	 * 过期不落库、在读的时候算：{@code expire_time < now} 就归到"已过期"。
	 * 这样不需要一个定时任务去扫全表改状态 —— 少一个组件、少一类故障模式。
	 * 代价是查询里多一个条件判断，可以接受。
	 *
	 * @param useType null = 全部；0 = 未使用且未过期；1 = 已使用；2 = 已过期
	 */
	List<MemberCouponVo> selectMemberCoupons(@Param("memberId") Long memberId,
	                                         @Param("useType") Integer useType,
	                                         @Param("now") Date now);

	/**
	 * 结算页可用的券：未使用、未过期，且订单金额达到门槛。
	 * <p>
	 * 门槛判断放在 SQL 里而不是查回来再过滤，是因为「不满门槛的券不该出现在
	 * 可选列表里」——让用户看到一张选不了的券，然后在提交时报错，是很差的交互。
	 * 但<b>后端在真正用券时仍然要再校验一遍门槛</b>：这个列表是给 UI 看的，
	 * 客户端可以提交任何 couponHistoryId，不能信。
	 */
	List<MemberCouponVo> selectUsableForAmount(@Param("memberId") Long memberId,
	                                           @Param("amount") java.math.BigDecimal amount,
	                                           @Param("now") Date now);

	/** 按 id + 会员查一张券的完整信息（含券面），用券前的服务端校验用。 */
	MemberCouponVo selectOwnedCoupon(@Param("id") Long id, @Param("memberId") Long memberId);
}
