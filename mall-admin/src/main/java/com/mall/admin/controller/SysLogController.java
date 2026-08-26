package com.mall.admin.controller;

import com.mall.admin.service.SysLogService;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志（只读）。为什么没有写入端，见 {@link SysLogService} 的类注释。
 */
@RestController
@RequestMapping("/sys/log")
public class SysLogController {

    private final SysLogService sysLogService;

    public SysLogController(SysLogService sysLogService) {
        this.sysLogService = sysLogService;
    }

    @GetMapping("/list")
    public R list(@RequestParam(value = "page", defaultValue = "1") int page,
                  @RequestParam(value = "limit", defaultValue = "10") int limit,
                  @RequestParam(value = "key", required = false) String key) {
        return R.ok().put("page", new PageUtils(sysLogService.page(page, limit, key)));
    }
}
