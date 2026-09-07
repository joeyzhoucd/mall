package com.mall.ware.controller;

import com.mall.common.utils.R;
import com.mall.ware.service.WareMqConsumeMessageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 库存侧的 MQ 消费幂等记录（wms_mq_consume_message）。
 *
 * <h3>只有读，没有写</h3>
 * 理由和订单侧完全相同（见 OrderMqConsumeMessageController 的说明），
 * 但库存这边的后果更直接：三个监听器
 * （stock-deduct / stock-fail / stock-release）都靠这张表保证
 * 「同一条消息只扣一次 / 只放一次库存」。删掉一条 SUCCESS 记录再让消息重投，
 * 就是<b>重复扣减或重复释放库存</b> —— 而库存数字对不上通常要过很久
 * 才会被发现，到那时已经很难追溯是哪一次手工操作造成的。
 */
@RestController
@RequestMapping("/ware/mq-consume")
public class WareMqConsumeMessageController {

    private final WareMqConsumeMessageService wareMqConsumeMessageService;

    public WareMqConsumeMessageController(WareMqConsumeMessageService wareMqConsumeMessageService) {
        this.wareMqConsumeMessageService = wareMqConsumeMessageService;
    }

    @GetMapping("/list")
    public R list(@RequestParam Map<String, Object> params) {
        return R.ok().put("page", wareMqConsumeMessageService.queryPage(params));
    }

    @GetMapping("/stats")
    public R stats() {
        return R.ok().put("counts", wareMqConsumeMessageService.statusCounts());
    }
}
