package com.mall.coupon.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.coupon.entity.HomeAdvEntity;
import com.mall.coupon.service.HomeAdvService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;




@RestController
@RequestMapping("coupon/homeadv")
public class HomeAdvController {
    @Autowired
    private HomeAdvService homeAdvService;

    
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = homeAdvService.queryPage(params);

        return R.ok().put("page", page);
    }


    
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		HomeAdvEntity homeAdv = homeAdvService.getById(id);

        return R.ok().put("homeAdv", homeAdv);
    }

    
    @RequestMapping("/save")
    public R save(@RequestBody HomeAdvEntity homeAdv){
		homeAdvService.save(homeAdv);

        return R.ok();
    }

    
    @RequestMapping("/update")
    public R update(@RequestBody HomeAdvEntity homeAdv){
		homeAdvService.updateById(homeAdv);

        return R.ok();
    }

    
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
		homeAdvService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

}
