package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;


/**
 * 优惠券领取记录。一行 = 某个会员手里的一张券。
 *
 * <h3>三处相对生成器原版的改动，每处都是正确性问题</h3>
 * <ol>
 *   <li><b>{@code orderSn} 从 {@code Long} 改成 {@code String}</b> ——
 *       列类型原本是 {@code bigint}，而 {@code oms_order.order_sn} 是 {@code char(32)}。
 *       写进去要么直接报错，要么在非严格模式下静默截断成一个错的数字；
 *       而用券失败的补偿逻辑要靠 {@code order_sn} 反查这张券，截断之后就再也找不回来了。
 *       表和实体一起改（见 migration-2026-09-08-coupon-claim.sql）。</li>
 *   <li><b>新增 {@code receiveSeq}</b> —— "这个人领这张券的第几张"。
 *       它是唯一索引 {@code uk_member_coupon_seq (member_id, coupon_id, receive_seq)}
 *       的第三列。这个索引保证<b>一个 seq 槽位只有一个赢家</b>，
 *       从而让应用层的「先 COUNT 再 INSERT」变得安全 ——
 *       输的那一方拿到 {@code DuplicateKeyException} 并重新计数再试。
 *       <b>但索引自己不构成限领</b>：给 seq 定上限的是应用层那道
 *       {@code already >= perLimit} 检查。实测两者缺一都会超领，
 *       详见 {@code CouponHistoryDao.countByMemberAndCoupon} 的注释。</li>
 *   <li><b>新增 {@code expireTime}</b> —— 领取那一刻从 {@code sms_coupon.end_time}
 *       拷过来的快照。<b>不能用券时去读 sms_coupon.end_time</b>：后台可以编辑已发布的券，
 *       运营把 end_time 改早会让已经发到用户手里的券<b>追溯失效</b>。
 *       券一旦发出，有效期就该冻结。</li>
 * </ol>
 *
 * <h3>没有 version 列，是刻意的</h3>
 * 用券走的是条件更新 {@code WHERE id = ? AND use_type = 0}，
 * 状态只能 0 -> 1 单向推进（补偿是 1 -> 0，但带 order_sn 限定），
 * 不存在 ABA，所以不需要额外的版本号。
 * 参照 mall-ware 的 {@code StockAtomicOps}：那里同样用状态本身当 CAS 的判据。
 */
@Data
@TableName("sms_coupon_history")
public class CouponHistoryEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	@TableId
	private Long id;

	private Long couponId;

	private Long memberId;

	private String memberNickName;

	/** 本人领取序号，1..per_limit。唯一索引的第三列，见类注释。 */
	private Integer receiveSeq;

	/** 获取方式[0->后台赠送；1->主动领取] */
	private Integer getType;

	private Date createTime;

	/** 领取时冻结的失效时间，快照自 sms_coupon.end_time，见类注释。 */
	private Date expireTime;

	/** 使用状态[0->未使用；1->已使用；2->已过期] */
	private Integer useType;

	private Date useTime;

	private Long orderId;

	/** 与 oms_order.order_sn 同为 char(32)，见类注释。 */
	private String orderSn;

}
