package com.mall.admin.controller;

import com.mall.admin.entity.SysConfigEntity;
import com.mall.admin.service.SysConfigService;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 系统参数配置。 */
@RestController
@RequestMapping("/sys/config")
public class SysConfigController {

    private final SysConfigService sysConfigService;

    public SysConfigController(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    @GetMapping("/list")
    public R list(@RequestParam(value = "page", defaultValue = "1") int page,
                  @RequestParam(value = "limit", defaultValue = "10") int limit,
                  @RequestParam(value = "paramKey", required = false) String paramKey) {
        return R.ok().put("page", new PageUtils(sysConfigService.page(page, limit, paramKey)));
    }

    @GetMapping("/info/{id}")
    public R info(@PathVariable("id") Long id) {
        return R.ok().put("config", sysConfigService.info(id));
    }

    @PostMapping("/save")
    public R save(@RequestBody SysConfigEntity config) {
        if (config.getParamKey() == null || config.getParamKey().isBlank()) {
            return R.error("参数名不能为空");
        }
        sysConfigService.save(config);
        return R.ok();
    }

    @PostMapping("/update")
    public R update(@RequestBody SysConfigEntity config) {
        if (config.getId() == null) {
            return R.error("参数ID不能为空");
        }
        sysConfigService.update(config);
        return R.ok();
    }

    @PostMapping("/delete")
    public R delete(@RequestBody List<Long> ids) {
        sysConfigService.delete(ids);
        return R.ok();
    }
}
