package com.mall.order.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.order.entity.PaymentInfoEntity;
import com.mall.order.service.PaymentInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;




@RestController
@RequestMapping("order/paymentinfo")
public class PaymentInfoController {
    @Autowired
    private PaymentInfoService paymentInfoService;

    
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = paymentInfoService.queryPage(params);

        return R.ok().put("page", page);
    }


    
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		PaymentInfoEntity paymentInfo = paymentInfoService.getById(id);

        return R.ok().put("paymentInfo", paymentInfo);
    }

    
    @RequestMapping("/save")
    public R save(@RequestBody PaymentInfoEntity paymentInfo){
		paymentInfoService.save(paymentInfo);

        return R.ok();
    }

    
    @RequestMapping("/update")
    public R update(@RequestBody PaymentInfoEntity paymentInfo){
		paymentInfoService.updateById(paymentInfo);

        return R.ok();
    }

    
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
		paymentInfoService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

}
