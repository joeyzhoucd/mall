package com.mall.ware.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.ware.entity.WareInfoEntity;
import com.mall.ware.service.WareInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;


@RestController
@RequestMapping("ware/wareinfo")
public class WareInfoController {
    @Autowired
    private WareInfoService wareInfoService;

    
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = wareInfoService.queryPage(params);
        return R.ok().put("page", page);
    }

    
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		WareInfoEntity wareInfo = wareInfoService.getById(id);
        return R.ok().put("wareInfo", wareInfo);
    }

    
    @RequestMapping("/save")
    public R save(@RequestBody WareInfoEntity wareInfo){
		wareInfoService.save(wareInfo);
        return R.ok();
    }

    
    @RequestMapping("/update")
    public R update(@RequestBody WareInfoEntity wareInfo){
		wareInfoService.updateById(wareInfo);
        return R.ok();
    }

    
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
		wareInfoService.removeByIds(Arrays.asList(ids));
        return R.ok();
    }
}
