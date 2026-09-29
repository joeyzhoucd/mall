package com.mall.order.controller;

import com.mall.common.utils.R;
import com.mall.order.submit.SubmitGate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 下单闸门的查看 / 运行时调参（校准压测、事故时临时收紧）。
 *
 * <p>只在集群内可达：网关的 InternalPathGuardFilter 对 /order/internal/** 一律 404（阶段 3 验证过 5 种变体）。
 * <b>每个 pod 各有一个闸门</b>，要改全部副本得逐个 pod IP 调；重启后回到配置值
 * （{@code mall.order.submit.gate.limit}）—— 定下来的值要写回配置，这里只是临时旋钮。
 */
@RestController
@RequestMapping("/order/internal/submit-gate")
public class SubmitGateInternalController {

    private static final Logger log = LoggerFactory.getLogger(SubmitGateInternalController.class);

    private final SubmitGate gate;

    public SubmitGateInternalController(SubmitGate gate) {
        this.gate = gate;
    }

    @GetMapping
    public R state() {
        return R.ok().put("limit", gate.limit()).put("inFlight", gate.inFlight()).put("rejected", gate.rejectedCount());
    }

    @PostMapping
    public R setLimit(@RequestParam("limit") int limit) {
        int before = gate.limit();
        gate.setLimit(limit);
        log.warn("下单闸门上限运行时调整 {} -> {}（本 pod，重启后回到配置值）", before, limit);
        return state();
    }
}
