package com.mall.mq.controller;

import com.mall.common.utils.R;
import com.mall.mq.service.MqDlqService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mq/dlq")
public class MqDlqController {

    private final MqDlqService mqDlqService;

    public MqDlqController(MqDlqService mqDlqService) {
        this.mqDlqService = mqDlqService;
    }

    @GetMapping("/queues")
    public R queues() {
        return R.ok().put("queues", mqDlqService.overview());
    }

    @GetMapping("/{dlq}/peek")
    public R peek(@PathVariable String dlq) {
        return R.ok().put("message", mqDlqService.peek(dlq));
    }

    @PostMapping("/{dlq}/replay")
    public R replay(@PathVariable String dlq, @RequestParam(required = false) Integer limit) {
        return R.ok().put("count", mqDlqService.replay(dlq, limit));
    }

    @PostMapping("/{dlq}/discard")
    public R discard(@PathVariable String dlq, @RequestParam(required = false) Integer limit) {
        return R.ok().put("count", mqDlqService.discard(dlq, limit));
    }
}
