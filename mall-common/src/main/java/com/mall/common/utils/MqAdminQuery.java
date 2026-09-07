package com.mall.common.utils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息治理页的查询条件拼装。
 *
 * <h3>为什么放在 mall-common 而不是各服务里各写一份</h3>
 * 订单和库存的 Outbox 表结构<b>逐列相同</b>（oms_order_outbox_message 与
 * wms_stock_outbox_message 只有表名和索引名不一样），消费幂等的两张表同理。
 * 四个服务各抄一遍的话，将来加一个筛选条件就要改四处，而漏掉其中一处
 * 不会有任何报错 —— 只会表现为「库存那页的这个筛选框没反应」。
 * 这个项目里「筛选框能输入但不生效」已经在品牌、属性分组上各撞过一次。
 *
 * <h3>返回 QueryWrapper 而不是直接查</h3>
 * 这样它是纯逻辑，不碰数据库，测试可以直接断言拼出来的 SQL 片段；
 * 而 SQL 片段恰好能同时验证「筛选加了没有」和「排序落在哪一列」两件事。
 * 见 MqAdminQueryTest。
 */
public final class MqAdminQuery {

    private MqAdminQuery() {
    }

    /**
     * 事务性 Outbox 的列表查询条件。
     *
     * <p>支持的筛选：{@code status} / {@code businessType} / {@code businessKey} /
     * {@code messageKey}（都是精确），以及 {@code key}（在 message_key 和
     * business_key 上模糊）。
     *
     * <h3>排序用 id desc</h3>
     * id 是 AUTO_INCREMENT，所以 id 降序就是「最新入队的在前」，
     * 而且它<b>唯一</b> —— 分页翻页时不会出现重复行或漏行。
     * 不用 create_time：同一次下单会在同一毫秒里塞进 2~3 条消息
     * （ORDER_CLOSE / STOCK_RELEASE / STOCK_DEDUCT），
     * 光按时间排的话这几条之间的顺序是未定义的。
     */
    public static <T> QueryWrapper<T> outbox(Map<String, Object> params) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();

        Integer status = intOrNull(params, "status");
        if (status != null) {
            wrapper.eq("status", status);
        }
        eqIfPresent(wrapper, params, "businessType", "business_type");
        eqIfPresent(wrapper, params, "businessKey", "business_key");
        eqIfPresent(wrapper, params, "messageKey", "message_key");

        String key = trimmed(params, "key");
        if (key != null) {
            // 【必须包在 and(...) 里】否则这个 or 会把前面所有条件都吞掉：
            // 拼出来会是「(status=4 AND message_key LIKE x) OR business_key LIKE x」，
            // 也就是右半边不受 status 约束 —— 筛「死信」却查出一堆已发送的。
            wrapper.and(w -> w.like("message_key", key).or().like("business_key", key));
        }

        return wrapper.orderByDesc("id");
    }

    /**
     * 消费幂等记录的列表查询条件。
     *
     * <p>支持的筛选：{@code status} / {@code consumerGroup} / {@code businessType} /
     * {@code messageKey}（精确），以及 {@code key}（在 message_key 上模糊）。
     *
     * <h3>排序用 update_time desc, id desc，和 Outbox 不一样</h3>
     * 这张表的记录会被<b>反复更新</b>（每次重投都会 consume_count+1、写 last_error），
     * 而运维真正要看的是「最近又出问题的是哪几条」。按 id 排的话，
     * 一条三天前创建、今天还在失败的记录会沉到很后面。
     * <p>
     * 但 update_time 不唯一（同一批重投会撞在同一秒），所以必须再用 id 收尾，
     * 否则并列行之间的顺序未定义，翻页照样会重复或漏掉。
     * 这也正好吃上 idx_*_mq_consume_status (status, update_time) 这个索引。
     */
    public static <T> QueryWrapper<T> consume(Map<String, Object> params) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();

        Integer status = intOrNull(params, "status");
        if (status != null) {
            wrapper.eq("status", status);
        }
        eqIfPresent(wrapper, params, "consumerGroup", "consumer_group");
        eqIfPresent(wrapper, params, "businessType", "business_type");
        eqIfPresent(wrapper, params, "messageKey", "message_key");

        String key = trimmed(params, "key");
        if (key != null) {
            wrapper.like("message_key", key);
        }

        return wrapper.orderByDesc("update_time").orderByDesc("id");
    }

    /**
     * 按状态分组统计的查询条件：{@code SELECT status, COUNT(*) AS cnt ... GROUP BY status}。
     *
     * <h3>为什么单独给一个接口，而不是让前端按状态查五次列表</h3>
     * 这两类页面第一眼要回答的问题是「<b>有没有卡住的</b>」，
     * 而不是「第 1 页有哪些」。如果只能靠切换状态筛选去发现 DEAD 有 37 条，
     * 那就等于要求看的人先怀疑、再去查 —— 没人会主动这么做。
     * 一次拿全所有状态的条数，异常状态就会自己跳出来。
     */
    public static <T> QueryWrapper<T> statusCountQuery() {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        wrapper.select("status", "COUNT(*) AS cnt").groupBy("status");
        return wrapper;
    }

    /**
     * 把 {@link #statusCountQuery()} 的原始结果转成 状态码 → 条数。
     *
     * <p>数据库返回的 {@code cnt} 的 Java 类型不确定（COUNT(*) 在不同驱动/方言下
     * 可能是 Long 也可能是 BigInteger/BigDecimal），所以统一按 {@link Number} 取，
     * 而不是强转成某一个具体类型 —— 强转错了是 ClassCastException，
     * 而它只会在真有数据的时候才炸，空表时反而一切正常。
     */
    public static Map<Integer, Long> toStatusCounts(List<Map<String, Object>> rows) {
        Map<Integer, Long> counts = new LinkedHashMap<>();
        if (rows == null) {
            return counts;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Object status = firstNonNull(row, "status", "STATUS");
            Object cnt = firstNonNull(row, "cnt", "CNT");
            if (status instanceof Number s && cnt instanceof Number c) {
                counts.put(s.intValue(), c.longValue());
            }
        }
        return counts;
    }

    /** 列名大小写取决于数据库和驱动，两种都试一次。 */
    private static Object firstNonNull(Map<String, Object> row, String... names) {
        for (String name : names) {
            Object value = row.get(name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static <T> void eqIfPresent(QueryWrapper<T> wrapper, Map<String, Object> params,
                                        String paramName, String column) {
        String value = trimmed(params, paramName);
        if (value != null) {
            wrapper.eq(column, value);
        }
    }

    /**
     * 取参数并去掉两端空白，全空白视同没传。
     *
     * <p>空白必须当成「没传」而不是「等于空串」：前端把一个清空了的输入框
     * 原样提交是很常见的，拼成 {@code business_type = ''} 会让列表一条都查不出来，
     * 而界面上看起来就像「没有数据」。
     */
    static String trimmed(Map<String, Object> params, String name) {
        Object raw = params == null ? null : params.get(name);
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return StringUtils.isEmpty(value) ? null : value;
    }

    /**
     * 解析成整数，解析不了就当没传。
     *
     * <p><b>不抛异常</b>：前端传来一个非法的筛选值是很正常的
     * （比如状态下拉框的 "全部" 被序列化成了 "undefined"），
     * 不该让整个列表接口 500 —— 那会让一个显示问题升级成一个可用性问题。
     */
    static Integer intOrNull(Map<String, Object> params, String name) {
        String value = trimmed(params, name);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
