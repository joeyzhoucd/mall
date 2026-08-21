package com.mall.order.controller;

import com.mall.common.utils.R;
import com.mall.order.constant.OrderConstant;
import com.mall.order.interceptor.OrderInterceptor;
import com.mall.order.service.OrderService;
import com.mall.order.to.UserInfoTo;
import com.mall.order.util.PaySignUtils;
import com.mall.order.vo.MemberAddressVo;
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

import javax.servlet.http.HttpServletRequest;
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

    @GetMapping("/order/address/add.html")
    public String addAddressPage() {
        UserInfoTo userInfoTo = OrderInterceptor.threadLocal.get();
        if (userInfoTo == null || userInfoTo.getUserId() == null) {
            return "redirect:http://auth.mall.com/login.html";
        }
        return "orderAddressAdd";
    }

    @PostMapping("/order/address/add")
    public String addAddress(MemberAddressVo addressVo, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        boolean ok = orderService.saveAddress(addressVo);
        if (!ok) {
            redirectAttributes.addFlashAttribute("errorMsg", "地址保存失败，请重试");
            return "redirect:" + externalBase(request) + "/order/address/add.html";
        }
        return "redirect:" + externalBase(request) + "/order/confirm.html";
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
    public String submitOrderPage(OrderSubmitVo submitVo, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        SubmitOrderResponseVo responseVo = orderService.submitOrder(submitVo);
        if (responseVo.getCode() != null && responseVo.getCode() == 0 && responseVo.getOrder() != null) {
            return "redirect:" + externalBase(request) + "/order/payment.html?orderSn=" + responseVo.getOrder().getOrderSn();
        }
        int code = responseVo.getCode() == null ? 1 : responseVo.getCode();
        redirectAttributes.addFlashAttribute("errorCode", code);
        if (code == 2) {
            redirectAttributes.addFlashAttribute("errorMsg", "订单价格已变化，请确认后重新提交");
        } else if (code == 3) {
            redirectAttributes.addFlashAttribute("errorMsg", "库存不足");
        } else if (code == 4) {
            redirectAttributes.addFlashAttribute("errorMsg", "请先添加收货地址");
        } else {
            redirectAttributes.addFlashAttribute("errorMsg", "订单提交失败");
        }
        return "redirect:" + externalBase(request) + "/order/confirm.html";
    }

    @GetMapping("/order/payment.html")
    public String paymentPage(@RequestParam("orderSn") String orderSn, Model model, HttpServletRequest request) {
        UserInfoTo userInfoTo = OrderInterceptor.threadLocal.get();
        if (userInfoTo == null || userInfoTo.getUserId() == null) {
            return "redirect:http://auth.mall.com/login.html";
        }
        com.mall.order.entity.OrderEntity order = orderService.getOrderBySn(orderSn);
        if (order == null) {
            return "redirect:" + externalBase(request) + "/order/confirm.html";
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
        } else if (code == 4) {
            msg = "请先添加收货地址";
        } else {
            msg = "订单提交失败";
        }
        return R.error(code, msg);
    }

    private String sign(String content) {
        return PaySignUtils.hmacSha256(content, signKey);
    }

    /**
     * mall-gateway 转发到 mall-order 时，Netty 客户端会把 Host 头改写成它实际连接的
     * 那个 pod 地址，Tomcat 没配 forward-headers-strategy 就不会去信 X-Forwarded-Host，
     * 相对路径的 redirect（"redirect:/xxx"）会被 Spring 拿这个被改写过的 Host 拼出
     * Location，变成一个集群外访问不到的 pod 内部地址。ingress-nginx 在最外层已经把
     * 真实域名放进 X-Forwarded-Host 了，这里手动读出来拼绝对地址，不依赖框架配置。
     *
     * 实测 ingress-nginx→mall-gateway 这两跳会各自往 X-Forwarded-Host/-Proto 追加一次，
     * 变成逗号分隔的多值（比如 "http,http"），不是单值——只取第一段，否则拼出来的
     * "http,http://cart.mall.com,cart.mall.com" 不是合法 URL，会被当成相对路径处理，
     * 又绕回 pod IP 那个坑（复现过一次，教训写在这）。
     */
    private String externalBase(HttpServletRequest request) {
        String host = firstValue(request.getHeader("X-Forwarded-Host"));
        if (host == null || host.isEmpty()) {
            host = firstValue(request.getHeader("Host"));
        }
        String proto = firstValue(request.getHeader("X-Forwarded-Proto"));
        if (proto == null || proto.isEmpty()) {
            proto = "http";
        }
        return proto + "://" + host;
    }

    private String firstValue(String header) {
        if (header == null) {
            return null;
        }
        int comma = header.indexOf(',');
        return (comma >= 0 ? header.substring(0, comma) : header).trim();
    }
}

