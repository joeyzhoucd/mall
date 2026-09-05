package com.mall.order.vo;

import com.mall.order.entity.OrderEntity;
import com.mall.order.entity.OrderItemEntity;
import com.mall.order.entity.OrderOperateHistoryEntity;
import lombok.Data;

import java.util.List;

/**
 * 后台的订单详情：订单本体 + 商品明细 + 操作记录。
 *
 * <h3>为什么做成一个聚合响应，而不是让前端调三个接口</h3>
 * 这三样在后台是<b>一个东西</b>——打开一个订单要同时看到「买了什么」和
 * 「经历过什么」。拆成三个请求有两个问题：
 * <ul>
 *   <li>三次往返，而且要在前端拼装「哪些明细属于这个订单」</li>
 *   <li>更要紧的是<b>可能拼出不一致的画面</b>：三次请求之间订单状态变了
 *       （比如刚好被发货），界面上就会出现「状态是待发货，但操作记录里
 *       已经有发货那一条」。一次查询取回来不会有这个缝。</li>
 * </ul>
 *
 * <h3>不做成分页</h3>
 * 一个订单的明细和操作记录都是十几条量级，分页只会让界面更啰嗦。
 * 真出现异常多的情况（比如被刷了几百条操作记录），那本身就是要看见的信号。
 */
@Data
public class OrderDetailVo {

    /** 订单本体。找不到订单时整个响应不会返回，所以这里不会是 null。 */
    private OrderEntity order;

    /** 商品明细，按 id 升序（也就是下单时的加入顺序）。 */
    private List<OrderItemEntity> items;

    /**
     * 操作记录，<b>按时间倒序</b>——最近发生的在最上面。
     * 排查订单问题时先看的总是「最后发生了什么」。
     */
    private List<OrderOperateHistoryEntity> history;
}
