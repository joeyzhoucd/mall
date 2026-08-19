package com.mall.order.controller;

import com.mall.common.utils.R;
import com.mall.order.constant.OrderConstant;
import com.mall.order.interceptor.OrderInterceptor;
import com.mall.order.service.OrderService;
import com.mall.order.to.UserInfoTo;
import com.mall.order.util.PaySignUtils;
import com.mall.order.vo.OrderConfirmVo;
import com.mall.order.vo.OrderSubmitVo;
import com.mall.order.vo.SubmitOrderResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.math.RoundingMode;
import java.util.UUID;

@Controller
public class OrderWebController {

    @Autowired
    private OrderService orderService;

    @Value("${pay.mock.signKey:mall-pay-sign-key}")
    private String signKey;

    @GetMapping("/order/confirm.html")
    public String confirmOrder(Model model, HttpSession session) {
        UserInfoTo userInfoTo = OrderInterceptor.threadLocal.get();
        if (userInfoTo == null || userInfoTo.getUserId() == null) {
            return "redirect:http://auth.mall.com/login.html";
        }

        OrderConfirmVo confirmVo = orderService.confirmOrder();
        String token = UUID.randomUUID().toString().replace("-", "");
        session.setAttribute(OrderConstant.ORDER_TOKEN_PREFIX + userInfoTo.getUserId(), token);
        confirmVo.setOrderToken(token);
        model.addAttribute("confirmVo", confirmVo);
        return "orderConfirm";
    }

    @GetMapping("/order/shipping.html")
    public String shippingPage(Model model) {
        UserInfoTo userInfoTo = OrderInterceptor.threadLocal.get();
        if (userInfoTo == null || userInfoTo.getUserId() == null) {
            return "redirect:http://auth.mall.com/login.html";
        }
        OrderConfirmVo confirmVo = orderService.confirmOrder();
        model.addAttribute("confirmVo", confirmVo);
        return "orderShipping";
    }

    @PostMapping("/order/submitOrder")
    public String submitOrderPage(OrderSubmitVo submitVo, RedirectAttributes redirectAttributes) {
        SubmitOrderResponseVo responseVo = orderService.submitOrder(submitVo);
        if (responseVo.getCode() != null && responseVo.getCode() == 0 && responseVo.getOrder() != null) {
            return "redirect:/order/payment.html?orderSn=" + responseVo.getOrder().getOrderSn();
        }
        int code = responseVo.getCode() == null ? 1 : responseVo.getCode();
        redirectAttributes.addFlashAttribute("errorCode", code);
        if (code == 2) {
            redirectAttributes.addFlashAttribute("errorMsg", "订单价格已变化，请确认后重新提交");
        } else if (code == 3) {
            redirectAttributes.addFlashAttribute("errorMsg", "库存不足");
        } else {
            redirectAttributes.addFlashAttribute("errorMsg", "订单提交失败");
        }
        return "redirect:/order/confirm.html";
    }

    @GetMapping("/order/payment.html")
    public String paymentPage(@RequestParam("orderSn") String orderSn, Model model) {
        UserInfoTo userInfoTo = OrderInterceptor.threadLocal.get();
        if (userInfoTo == null || userInfoTo.getUserId() == null) {
            return "redirect:http://auth.mall.com/login.html";
        }
        com.mall.order.entity.OrderEntity order = orderService.getOrderBySn(orderSn);
        if (order == null) {
            return "redirect:/order/confirm.html";
        }

        String totalAmount = (order.getPayAmount() == null ? "0.00" :
                order.getPayAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());

        model.addAttribute("order", order);
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("signSuccess", sign("orderSn=" + orderSn + "&tradeStatus=TRADE_SUCCESS&totalAmount=" + totalAmount));
        model.addAttribute("signClosed", sign("orderSn=" + orderSn + "&tradeStatus=TRADE_CLOSED&totalAmount=" + totalAmount));
        model.addAttribute("signFail", sign("orderSn=" + orderSn + "&tradeStatus=FAIL&totalAmount=" + totalAmount));
        return "orderPayment";
    }

    @ResponseBody
    @PostMapping("/order/submit")
    public R submitOrder(OrderSubmitVo submitVo) {
        SubmitOrderResponseVo responseVo = orderService.submitOrder(submitVo);
        if (responseVo.getCode() != null && responseVo.getCode() == 0) {
            return R.ok().put("order", responseVo.getOrder());
        }
        int code = responseVo.getCode() == null ? 1 : responseVo.getCode();
        String msg;
        if (code == 2) {
            msg = "订单价格已变化，请确认后重新提交";
        } else if (code == 3) {
            msg = "库存不足";
        } else {
            msg = "订单提交失败";
        }
        return R.error(code, msg);
    }

    private String sign(String content) {
        return PaySignUtils.hmacSha256(content, signKey);
    }
}

