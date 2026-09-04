package com.mall.ware.controller;

import com.mall.common.utils.R;
import com.mall.ware.service.StockOutboxMessageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/ware/outbox")
public class StockOutboxMessageController {

    private final StockOutboxMessageService stockOutboxMessageService;

    public StockOutboxMessageController(StockOutboxMessageService stockOutboxMessageService) {
        this.stockOutboxMessageService = stockOutboxMessageService;
    }

    @GetMapping("/list")
    public R list(@RequestParam Map<String, Object> params) {
        return R.ok().put("page", stockOutboxMessageService.queryPage(params));
    }

    @PostMapping("/publish")
    public R publishReadyMessages() {
        return R.ok().put("count", stockOutboxMessageService.publishReadyMessages());
    }

    @PostMapping("/{id}/resend")
    public R resend(@PathVariable Long id) {
        return R.ok().put("resend", stockOutboxMessageService.resend(id));
    }

    @PostMapping("/{id}/dead")
    public R markDead(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return R.ok().put("dead", stockOutboxMessageService.markDead(id, reason));
    }
}
