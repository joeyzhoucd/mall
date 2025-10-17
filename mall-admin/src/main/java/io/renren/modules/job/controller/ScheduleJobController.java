/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.job.controller;

import io.renren.common.annotation.SysLog;
import io.renren.common.utils.PageUtils;
import io.renren.common.utils.R;
import io.renren.common.validator.ValidatorUtils;
import io.renren.modules.job.entity.ScheduleJobEntity;
import io.renren.modules.job.service.ScheduleJobService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * å®šæ—¶ä»»åŠ¡
 *
 * @author Mark sunlightcs@gmail.com
 */
@RestController
@RequestMapping("/sys/schedule")
public class ScheduleJobController {
	@Autowired
	private ScheduleJobService scheduleJobService;
	
	/**
	 * å®šæ—¶ä»»åŠ¡åˆ—è¡¨
	 */
	@RequestMapping("/list")
	@RequiresPermissions("sys:schedule:list")
	public R list(@RequestParam Map<String, Object> params){
		PageUtils page = scheduleJobService.queryPage(params);

		return R.ok().put("page", page);
	}
	
	/**
	 * å®šæ—¶ä»»åŠ¡ä¿¡æ¯
	 */
	@RequestMapping("/info/{jobId}")
	@RequiresPermissions("sys:schedule:info")
	public R info(@PathVariable("jobId") Long jobId){
		ScheduleJobEntity schedule = scheduleJobService.getById(jobId);
		
		return R.ok().put("schedule", schedule);
	}
	
	/**
	 * ä¿å­˜å®šæ—¶ä»»åŠ¡
	 */
	@SysLog("ä¿å­˜å®šæ—¶ä»»åŠ¡")
	@RequestMapping("/save")
	@RequiresPermissions("sys:schedule:save")
	public R save(@RequestBody ScheduleJobEntity scheduleJob){
		ValidatorUtils.validateEntity(scheduleJob);
		
		scheduleJobService.saveJob(scheduleJob);
		
		return R.ok();
	}
	
	/**
	 * ä¿®æ”¹å®šæ—¶ä»»åŠ¡
	 */
	@SysLog("ä¿®æ”¹å®šæ—¶ä»»åŠ¡")
	@RequestMapping("/update")
	@RequiresPermissions("sys:schedule:update")
	public R update(@RequestBody ScheduleJobEntity scheduleJob){
		ValidatorUtils.validateEntity(scheduleJob);
				
		scheduleJobService.update(scheduleJob);
		
		return R.ok();
	}
	
	/**
	 * åˆ é™¤å®šæ—¶ä»»åŠ¡
	 */
	@SysLog("åˆ é™¤å®šæ—¶ä»»åŠ¡")
	@RequestMapping("/delete")
	@RequiresPermissions("sys:schedule:delete")
	public R delete(@RequestBody Long[] jobIds){
		scheduleJobService.deleteBatch(jobIds);
		
		return R.ok();
	}
	
	/**
	 * ç«‹å³æ‰§è¡Œä»»åŠ¡
	 */
	@SysLog("ç«‹å³æ‰§è¡Œä»»åŠ¡")
	@RequestMapping("/run")
	@RequiresPermissions("sys:schedule:run")
	public R run(@RequestBody Long[] jobIds){
		scheduleJobService.run(jobIds);
		
		return R.ok();
	}
	
	/**
	 * æš‚åœå®šæ—¶ä»»åŠ¡
	 */
	@SysLog("æš‚åœå®šæ—¶ä»»åŠ¡")
	@RequestMapping("/pause")
	@RequiresPermissions("sys:schedule:pause")
	public R pause(@RequestBody Long[] jobIds){
		scheduleJobService.pause(jobIds);
		
		return R.ok();
	}
	
	/**
	 * æ¢å¤å®šæ—¶ä»»åŠ¡
	 */
	@SysLog("æ¢å¤å®šæ—¶ä»»åŠ¡")
	@RequestMapping("/resume")
	@RequiresPermissions("sys:schedule:resume")
	public R resume(@RequestBody Long[] jobIds){
		scheduleJobService.resume(jobIds);
		
		return R.ok();
	}

}
