package com.mall.order.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.order.entity.OrderSettingEntity;
import com.mall.order.service.OrderSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;




@RestController
@RequestMapping("order/ordersetting")
public class OrderSettingController {
    @Autowired
    private OrderSettingService orderSettingService;

    
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = orderSettingService.queryPage(params);

        return R.ok().put("page", page);
    }


    
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		OrderSettingEntity orderSetting = orderSettingService.getById(id);

        return R.ok().put("orderSetting", orderSetting);
    }

    
    @RequestMapping("/save")
    public R save(@RequestBody OrderSettingEntity orderSetting){
		orderSettingService.save(orderSetting);

        return R.ok();
    }

    
    @RequestMapping("/update")
    public R update(@RequestBody OrderSettingEntity orderSetting){
		orderSettingService.updateById(orderSetting);

        return R.ok();
    }

    
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
		orderSettingService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

}
