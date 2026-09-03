package com.mall.coupon.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 后台「给某个 SKU 配置秒杀」的请求体。
 *
 * <h3>为什么时间是 String 而不是 Date</h3>
 * 这里刻意收 {@code "yyyy-MM-dd HH:mm:ss"} 形式的<b>墙上时间</b>字符串，
 * 由服务端用固定格式解析，而不是让 Jackson 直接绑成 {@code java.util.Date}。
 * <p>
 * 原因是时区。数据库里 {@code start_time}/{@code end_time} 是 MySQL 的
 * {@code datetime}——<b>不带时区</b>，存的就是墙上时间。而容器时区是 UTC、
 * 业务时区声明的是 Asia/Shanghai。如果让 Jackson 绑 Date：
 * <ul>
 *   <li>前端 el-date-picker 不写 {@code value-format} 时传的是 JS Date 序列化出来的
 *       UTC ISO 串（{@code 2026-09-03T02:00:00.000Z}），绑成 Date 再写库会差 8 小时；</li>
 *   <li>就算加 {@code @JsonFormat(timezone="GMT+8")}，Date 是个瞬时点，
 *       MyBatis 写回去时又按 JVM 时区（UTC）折算一次，同样会偏。</li>
 * </ul>
 * 收字符串 + {@code LocalDateTime} 解析则完全绕开这件事：解析和写入用同一个
 * {@code ZoneId.systemDefault()}，一进一出抵消，落库的墙上时间和管理员填的一致。
 * 前端那边配套加了 {@code value-format="yyyy-MM-dd HH:mm:ss"}。
 */
@Data
public class SeckillSchedulerVo {

    /** 要配置秒杀的 SKU。 */
    private Long skuId;

    /** 场次开始时间，格式 {@code yyyy-MM-dd HH:mm:ss}。 */
    private String startTime;

    /** 场次结束时间，格式 {@code yyyy-MM-dd HH:mm:ss}。 */
    private String endTime;

    /** 秒杀价。列已从 decimal(10,0) 放宽到 (10,2)，见 migration-2026-09-03-seckill-price-scale.sql。 */
    private BigDecimal seckillPrice;

    /** 秒杀总量。注意它只是「配置值」，真实库存在 Redis，要 activate 才生效。 */
    private BigDecimal seckillCount;

    /** 每人限购数量。 */
    private BigDecimal seckillLimit;

    /** 排序，可空。 */
    private Integer seckillSort;
}
