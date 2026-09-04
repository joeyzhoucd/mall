package com.mall.order.controller;

import com.mall.common.utils.R;
import com.mall.order.service.OrderOutboxMessageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/order/outbox")
public class OrderOutboxMessageController {

    private final OrderOutboxMessageService orderOutboxMessageService;

    public OrderOutboxMessageController(OrderOutboxMessageService orderOutboxMessageService) {
        this.orderOutboxMessageService = orderOutboxMessageService;
    }

    @GetMapping("/list")
    public R list(@RequestParam Map<String, Object> params) {
        return R.ok().put("page", orderOutboxMessageService.queryPage(params));
    }

    @PostMapping("/publish")
    public R publishReadyMessages() {
        return R.ok().put("count", orderOutboxMessageService.publishReadyMessages());
    }

    @PostMapping("/{id}/resend")
    public R resend(@PathVariable Long id) {
        return R.ok().put("resend", orderOutboxMessageService.resend(id));
    }

    @PostMapping("/{id}/dead")
    public R markDead(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return R.ok().put("dead", orderOutboxMessageService.markDead(id, reason));
    }
}
