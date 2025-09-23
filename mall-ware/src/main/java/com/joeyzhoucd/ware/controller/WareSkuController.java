package com.joeyzhoucd.ware.controller;

import com.joeyzhoucd.common.utils.R;
import com.joeyzhoucd.ware.service.WareSkuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品库存控制器
 * 保留基础结构，删除前端未使用的方法
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:27:58
 */
@RestController
@RequestMapping("ware/waresku")
public class WareSkuController {
    @Autowired
    private WareSkuService wareSkuService;

    /**
     * 预留接口 - 仓储功能待开发
     */
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "仓储功能待开发");
    }
}
