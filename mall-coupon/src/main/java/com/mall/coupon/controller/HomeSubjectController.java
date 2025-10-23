package com.mall.coupon.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.coupon.entity.HomeSubjectEntity;
import com.mall.coupon.service.HomeSubjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;




@RestController
@RequestMapping("coupon/homesubject")
public class HomeSubjectController {
    @Autowired
    private HomeSubjectService homeSubjectService;

    
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = homeSubjectService.queryPage(params);

        return R.ok().put("page", page);
    }


    
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		HomeSubjectEntity homeSubject = homeSubjectService.getById(id);

        return R.ok().put("homeSubject", homeSubject);
    }

    
    @RequestMapping("/save")
    public R save(@RequestBody HomeSubjectEntity homeSubject){
		homeSubjectService.save(homeSubject);

        return R.ok();
    }

    
    @RequestMapping("/update")
    public R update(@RequestBody HomeSubjectEntity homeSubject){
		homeSubjectService.updateById(homeSubject);

        return R.ok();
    }

    
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
		homeSubjectService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

}
