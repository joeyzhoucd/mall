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

    /**
     * 各状态条数。
     *
     * <p>Outbox 页第一眼要回答的是「有没有卡住的」，而不是「第一页有哪些」。
     * 只给列表的话，DEAD 有 37 条这件事要求看的人<b>先怀疑、再去筛</b>，
     * 而没人会主动这么做 —— 一次把所有状态的条数拿全，异常会自己跳出来。
     */
    @GetMapping("/stats")
    public R stats() {
        return R.ok().put("counts", orderOutboxMessageService.statusCounts());
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
