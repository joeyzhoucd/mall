package com.mall.coupon.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.coupon.entity.HomeSubjectEntity;
import com.mall.coupon.service.HomeSubjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;



/**
 * é¦–é¡µä¸“é¢˜è¡¨ã€jdé¦–é¡µä¸‹é¢å¾ˆå¤šä¸“é¢˜ï¼Œæ¯ä¸ªä¸“é¢˜é“¾æŽ¥æ–°çš„é¡µé¢ï¼Œå±•ç¤ºä¸“é¢˜å•†å“ä¿¡æ¯ã€‘
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:08:26
 */
@RestController
@RequestMapping("coupon/homesubject")
public class HomeSubjectController {
    @Autowired
    private HomeSubjectService homeSubjectService;

    /**
     * åˆ—è¡¨
     */
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = homeSubjectService.queryPage(params);

        return R.ok().put("page", page);
    }


    /**
     * ä¿¡æ¯
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		HomeSubjectEntity homeSubject = homeSubjectService.getById(id);

        return R.ok().put("homeSubject", homeSubject);
    }

    /**
     * ä¿å­˜
     */
    @RequestMapping("/save")
    public R save(@RequestBody HomeSubjectEntity homeSubject){
		homeSubjectService.save(homeSubject);

        return R.ok();
    }

    /**
     * ä¿®æ”¹
     */
    @RequestMapping("/update")
    public R update(@RequestBody HomeSubjectEntity homeSubject){
		homeSubjectService.updateById(homeSubject);

        return R.ok();
    }

    /**
     * åˆ é™¤
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
		homeSubjectService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

}
