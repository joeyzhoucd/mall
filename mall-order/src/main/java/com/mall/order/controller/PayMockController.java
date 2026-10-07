package com.mall.order.controller;

import com.mall.common.constant.ErrorCode;
import com.mall.common.utils.R;
import com.mall.order.service.OrderService;
import com.mall.order.util.PaySignUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pay/mock")
public class PayMockController {

    @Autowired
    private OrderService orderService;

    @Value("${pay.mock.signKey}")
    private String signKey;

    // 2026-10-07 删掉了 /success /fail /close 三个接口：不验签，传任意 orderSn 就能把订单置为已支付 / 关闭。
    // 网关从没放行过它们（白名单只有 /notify），但集群内任何 pod 都能直接调；全仓没有调用方
    // （支付页的三个按钮走的都是下面这个带签名的 /notify）。

    /**
     * Mock async notify with sign verify
     */
    @PostMapping("/notify")
    public R payNotify(@RequestParam("orderSn") String orderSn,
                       @RequestParam("tradeStatus") String tradeStatus,
                       @RequestParam(value = "totalAmount", required = false) String totalAmount,
                       @RequestParam("sign") String sign) {
        String content = buildSignContent(orderSn, tradeStatus, totalAmount);
        String expected = PaySignUtils.hmacSha256(content, signKey);
        if (!StringUtils.equalsIgnoreCase(expected, sign)) {
            return R.error(ErrorCode.PAY_SIGN_INVALID);
        }
        if ("TRADE_SUCCESS".equalsIgnoreCase(tradeStatus)) {
            orderService.payOrderSuccess(orderSn);
            return R.ok().put("status", "SUCCESS");
        }
        if ("TRADE_CLOSED".equalsIgnoreCase(tradeStatus)) {
            orderService.closeOrder(orderSn);
            return R.ok().put("status", "CLOSED");
        }
        return R.ok().put("status", "FAIL");
    }

    private String buildSignContent(String orderSn, String tradeStatus, String totalAmount) {
        StringBuilder sb = new StringBuilder();
        sb.append("orderSn=").append(orderSn).append("&tradeStatus=").append(tradeStatus);
        if (StringUtils.isNotBlank(totalAmount)) {
            sb.append("&totalAmount=").append(totalAmount);
        }
        return sb.toString();
    }
}

