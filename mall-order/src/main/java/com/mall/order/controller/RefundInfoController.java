package com.mall.order.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.order.entity.RefundInfoEntity;
import com.mall.order.service.RefundInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;




@RestController
@RequestMapping("order/refundinfo")
public class RefundInfoController {
    @Autowired
    private RefundInfoService refundInfoService;

    
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = refundInfoService.queryPage(params);

        return R.ok().put("page", page);
    }


    
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		RefundInfoEntity refundInfo = refundInfoService.getById(id);

        return R.ok().put("refundInfo", refundInfo);
    }

    
    @RequestMapping("/save")
    public R save(@RequestBody RefundInfoEntity refundInfo){
		refundInfoService.save(refundInfo);

        return R.ok();
    }

    
    @RequestMapping("/update")
    public R update(@RequestBody RefundInfoEntity refundInfo){
		refundInfoService.updateById(refundInfo);

        return R.ok();
    }

    
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
		refundInfoService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

}
