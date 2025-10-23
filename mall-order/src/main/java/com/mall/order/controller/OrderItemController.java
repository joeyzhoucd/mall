package com.mall.order.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.order.entity.OrderItemEntity;
import com.mall.order.service.OrderItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;




@RestController
@RequestMapping("order/orderitem")
public class OrderItemController {
    @Autowired
    private OrderItemService orderItemService;

    
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = orderItemService.queryPage(params);

        return R.ok().put("page", page);
    }


    
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		OrderItemEntity orderItem = orderItemService.getById(id);

        return R.ok().put("orderItem", orderItem);
    }

    
    @RequestMapping("/save")
    public R save(@RequestBody OrderItemEntity orderItem){
		orderItemService.save(orderItem);

        return R.ok();
    }

    
    @RequestMapping("/update")
    public R update(@RequestBody OrderItemEntity orderItem){
		orderItemService.updateById(orderItem);

        return R.ok();
    }

    
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
		orderItemService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

}
