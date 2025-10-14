package com.joeyzhoucd.ware.controller;

import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.common.utils.R;
import com.joeyzhoucd.ware.entity.PurchaseDetailEntity;
import com.joeyzhoucd.ware.service.PurchaseDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("ware/purchasedetail")
public class PurchaseDetailController {

    @Autowired
    private PurchaseDetailService purchaseDetailService;

    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params) {
        PageUtils page = purchaseDetailService.queryPage(params);
        return R.ok().put("page", page);
    }

    @RequestMapping("/save")
    public R save(@RequestBody PurchaseDetailEntity entity) {
        purchaseDetailService.save(entity);
        return R.ok();
    }

    @RequestMapping("/update")
    public R update(@RequestBody PurchaseDetailEntity entity) {
        purchaseDetailService.updateById(entity);
        return R.ok();
    }

    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids) {
        purchaseDetailService.removeByIds(Arrays.asList(ids));
        return R.ok();
    }
}
