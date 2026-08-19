package com.mall.order.controller;

import com.mall.common.constant.ErrorCode;
import com.mall.common.utils.R;
import com.mall.order.service.OrderService;
import com.mall.order.util.PaySignUtils;
import org.apache.commons.lang.StringUtils;
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

    @PostMapping("/success")
    public R paySuccess(@RequestParam("orderSn") String orderSn) {
        orderService.payOrderSuccess(orderSn);
        return R.ok().put("status", "SUCCESS");
    }

    @PostMapping("/fail")
    public R payFail(@RequestParam("orderSn") String orderSn) {
        return R.ok().put("status", "FAIL");
    }

    @PostMapping("/close")
    public R payClose(@RequestParam("orderSn") String orderSn) {
        orderService.closeOrder(orderSn);
        return R.ok().put("status", "CLOSED");
    }

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

