package com.mall.coupon.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.coupon.entity.CouponEntity;
import com.mall.coupon.vo.PromotionCouponVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;


/**
 * 券本体的 DAO。
 *
 * <h3>为什么不能用 updateById 改 receive_count</h3>
 * MyBatis-Plus 的 {@code updateById} 会把实体里所有非空字段整行写回，
 * 于是两个并发请求各自读到 {@code receive_count = 7}、各自写回 8，
 * 实际只算了一次 —— 这是典型的丢更新，发行 10 张能发出十几张。
 * <p>
 * 更隐蔽的是它还会把内存里那份<b>已经过期的其他列</b>一起写回去
 * （比如运营刚在后台改的 publish_count 会被旧值覆盖）。
 * mall-ware 的 {@code incrementRetryIfLocked} 注释里记录过同一个坑，
 * 那次的后果是库存被释放两次。
 * <p>
 * 所以这里只写需要变的那一列，并且把判断条件写进 WHERE。
 */
@Mapper
public interface CouponDao extends BaseMapper<CouponEntity> {

	/**
	 * 占一张券的名额 —— <b>总量不超发的唯一保证</b>。
	 * <pre>
	 * UPDATE sms_coupon SET receive_count = receive_count + 1
	 *  WHERE id = ? AND publish = 1 AND receive_count &lt; publish_count
	 * </pre>
	 * 单条 UPDATE 在 InnoDB 里是原子的：行锁拿到之后条件才求值，
	 * 所以不存在"两个人都看到还剩 1 张"。不需要 version 列 ——
	 * {@code receive_count} 单调递增，不会有 ABA。
	 * <p>
	 * <b>注意 {@code publish_count} 和 {@code receive_count} 必须是 NOT NULL。</b>
	 * SQL 里 NULL 参与比较得到 UNKNOWN（既不是 TRUE 也不是 FALSE），
	 * 任何一边为 NULL 都会让这条 WHERE 永远不成立 —— 表现是"一张都领不到"，
	 * 而且没有任何报错。建表时这几列是 DEFAULT NULL，
	 * migration-2026-09-08-coupon-claim.sql 把它们改成了 NOT NULL DEFAULT 0。
	 *
	 * @return 1 = 占到了；0 = 已领完 / 未发布 / 券不存在
	 */
	int tryReserveOne(@Param("id") Long id);

	/**
	 * 归还一张名额。用于"名额已占、但后续步骤失败"的补偿。
	 * <p>
	 * 带 {@code receive_count > 0} 是为了防止补偿重投把计数压成负数 ——
	 * 负数会让 {@code receive_count < publish_count} 永远成立，等于把发行上限失效。
	 *
	 * @return 1 = 归还成功；0 = 计数已经是 0（重复补偿，安全忽略）
	 */
	int releaseOne(@Param("id") Long id);

	/** 用券时累加已使用数。纯统计，失败不影响业务，但同样只写一列。 */
	int incrementUseCount(@Param("id") Long id);

	/** 用券补偿时回退已使用数。带 {@code use_count > 0} 防止压成负数。 */
	int decrementUseCount(@Param("id") Long id);

	/**
	 * 促销页的券列表：已发布、且在领取时间窗内。
	 * <p>
	 * {@code publish = 0} 的券（草稿）必须不出现 —— 否则运营还没准备好的活动
	 * 就被用户领走了，而且没法撤回（券已经在用户手里）。
	 * <p>
	 * 已领完的券<b>仍然返回</b>，前端显示成"已抢光"。这是有意的：
	 * 把它从列表里去掉，用户会以为活动不存在；显示成已抢光才是真实状态。
	 * 所以这里不加 {@code receive_count < publish_count} 条件。
	 */
	List<PromotionCouponVo> selectPromotionCoupons(@Param("now") Date now);
}
