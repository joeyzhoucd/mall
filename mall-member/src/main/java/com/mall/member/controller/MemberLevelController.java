package com.mall.member.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.member.entity.MemberLevelEntity;
import com.mall.member.service.MemberLevelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;




@RestController
@RequestMapping("member/memberlevel")
public class MemberLevelController {
    @Autowired
    private MemberLevelService memberLevelService;

    
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = memberLevelService.queryPage(params);

        return R.ok().put("page", page);
    }


    
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		MemberLevelEntity memberLevel = memberLevelService.getById(id);

        return R.ok().put("memberLevel", memberLevel);
    }

    
    @RequestMapping("/save")
    public R save(@RequestBody MemberLevelEntity memberLevel){
		memberLevelService.save(memberLevel);

        return R.ok();
    }

    
    @RequestMapping("/update")
    public R update(@RequestBody MemberLevelEntity memberLevel){
		memberLevelService.updateById(memberLevel);

        return R.ok();
    }

    
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
		memberLevelService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

}
