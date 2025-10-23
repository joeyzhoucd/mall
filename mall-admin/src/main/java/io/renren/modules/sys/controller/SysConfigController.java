package io.renren.modules.sys.controller;

import io.renren.common.annotation.SysLog;
import io.renren.common.utils.PageUtils;
import io.renren.common.utils.R;
import io.renren.common.validator.ValidatorUtils;
import io.renren.modules.sys.entity.SysConfigEntity;
import io.renren.modules.sys.service.SysConfigService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * System configuration controller
 */
@RestController
@RequestMapping("/sys/config")
public class SysConfigController extends AbstractController {
	@Autowired
	private SysConfigService sysConfigService;
	
	/**
	 * List configurations
	 */
	@GetMapping("/list")
	@RequiresPermissions("sys:config:list")
	public R list(@RequestParam Map<String, Object> params){
		PageUtils page = sysConfigService.queryPage(params);

		return R.ok().put("page", page);
	}
	
	/**
	 * Get configuration info
	 */
	@GetMapping("/info/{id}")
	@RequiresPermissions("sys:config:info")
	public R info(@PathVariable("id") Long id){
		SysConfigEntity config = sysConfigService.getById(id);
		
		return R.ok().put("config", config);
	}
	
	/**
	 * Save configuration
	 */
	@SysLog("Save configuration")
	@PostMapping("/save")
	@RequiresPermissions("sys:config:save")
	public R save(@RequestBody SysConfigEntity config){
		ValidatorUtils.validateEntity(config);

		sysConfigService.saveConfig(config);
		
		return R.ok();
	}
	
	/**
	 * Update configuration
	 */
	@SysLog("Update configuration")
	@PostMapping("/update")
	@RequiresPermissions("sys:config:update")
	public R update(@RequestBody SysConfigEntity config){
		ValidatorUtils.validateEntity(config);
		
		sysConfigService.update(config);
		
		return R.ok();
	}
	
	/**
	 * Delete configuration
	 */
	@SysLog("Delete configuration")
	@PostMapping("/delete")
	@RequiresPermissions("sys:config:delete")
	public R delete(@RequestBody Long[] ids){
		sysConfigService.deleteBatch(ids);
		
		return R.ok();
	}

}