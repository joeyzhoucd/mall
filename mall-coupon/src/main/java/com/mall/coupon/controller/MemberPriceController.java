package com.mall.coupon.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.coupon.entity.MemberPriceEntity;
import com.mall.coupon.service.MemberPriceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;




@RestController
@RequestMapping("coupon/memberprice")
public class MemberPriceController {
    @Autowired
    private MemberPriceService memberPriceService;

    
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = memberPriceService.queryPage(params);

        return R.ok().put("page", page);
    }


    
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		MemberPriceEntity memberPrice = memberPriceService.getById(id);

        return R.ok().put("memberPrice", memberPrice);
    }

    
    @RequestMapping("/save")
    public R save(@RequestBody MemberPriceEntity memberPrice){
		memberPriceService.save(memberPrice);

        return R.ok();
    }

    
    @PostMapping("/saveFromMap")
    public R saveFromMap(@RequestBody Map<String, String> params){
        MemberPriceEntity memberPrice = new MemberPriceEntity();
        memberPrice.setSkuId(Long.valueOf(params.get("skuId")));
        memberPrice.setMemberLevelId(Long.valueOf(params.get("memberLevelId")));
        memberPrice.setMemberLevelName(params.get("memberLevelName"));
        memberPrice.setMemberPrice(new java.math.BigDecimal(params.get("memberPrice")));
        memberPrice.setAddOther(Integer.valueOf(params.get("addOther")));
        memberPriceService.save(memberPrice);

        return R.ok();
    }

    
    @RequestMapping("/update")
    public R update(@RequestBody MemberPriceEntity memberPrice){
		memberPriceService.updateById(memberPrice);

        return R.ok();
    }

    
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
		memberPriceService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

}
