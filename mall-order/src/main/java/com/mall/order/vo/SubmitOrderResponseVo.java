package com.mall.order.vo;

import com.mall.order.entity.OrderEntity;
import lombok.Data;

@Data
public class SubmitOrderResponseVo {
    /**
     * 下单结果码。
     * <ul>
     *   <li>0 成功</li>
     *   <li>1 未登录 / 令牌无效（重复提交）/ 落库失败 —— 三种混在一起是既有行为，
     *       靠 metrics 的 reason 标签区分（unauthenticated / duplicate_submit / persist_failed）</li>
     *   <li>2 价格已变动</li>
     *   <li>3 库存锁定失败</li>
     *   <li>4 收货地址无效</li>
     *   <li><b>5 优惠券不可用</b> —— 已被并发订单用掉、已过期、不满门槛、不属于此人。
     *       和 2/3 分开是为了让前端能给出有用的提示（"券已失效，请重新选择"
     *       而不是笼统的"下单失败"），而且这三种失败的用户动作完全不同。</li>
     * </ul>
     */
    private Integer code;
    private OrderEntity order;
}

