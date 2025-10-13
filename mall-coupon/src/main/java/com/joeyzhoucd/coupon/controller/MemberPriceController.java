package com.joeyzhoucd.coupon.controller;

import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.common.utils.R;
import com.joeyzhoucd.coupon.entity.MemberPriceEntity;
import com.joeyzhoucd.coupon.service.MemberPriceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;



/**
 * 商品会员价格
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:08:26
 */
@RestController
@RequestMapping("coupon/memberprice")
public class MemberPriceController {
    @Autowired
    private MemberPriceService memberPriceService;

    /**
     * 列表
     */
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = memberPriceService.queryPage(params);

        return R.ok().put("page", page);
    }


    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		MemberPriceEntity memberPrice = memberPriceService.getById(id);

        return R.ok().put("memberPrice", memberPrice);
    }

    /**
     * 保存
     */
    @RequestMapping("/save")
    public R save(@RequestBody MemberPriceEntity memberPrice){
		memberPriceService.save(memberPrice);

        return R.ok();
    }

    /**
     * 保存（通过Map参数）
     */
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

    /**
     * 修改
     */
    @RequestMapping("/update")
    public R update(@RequestBody MemberPriceEntity memberPrice){
		memberPriceService.updateById(memberPrice);

        return R.ok();
    }

    /**
     * 删除
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
		memberPriceService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

}
