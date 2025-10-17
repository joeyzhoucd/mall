/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.sys.controller;

import io.renren.common.annotation.SysLog;
import io.renren.common.utils.Constant;
import io.renren.common.utils.PageUtils;
import io.renren.common.utils.R;
import io.renren.common.validator.Assert;
import io.renren.common.validator.ValidatorUtils;
import io.renren.common.validator.group.AddGroup;
import io.renren.common.validator.group.UpdateGroup;
import io.renren.modules.sys.entity.SysUserEntity;
import io.renren.modules.sys.form.PasswordForm;
import io.renren.modules.sys.service.SysUserRoleService;
import io.renren.modules.sys.service.SysUserService;
import org.apache.commons.lang.ArrayUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ç³»ç»Ÿç”¨æˆ·
 *
 * @author Mark sunlightcs@gmail.com
 */
@RestController
@RequestMapping("/sys/user")
public class SysUserController extends AbstractController {
	@Autowired
	private SysUserService sysUserService;
	@Autowired
	private SysUserRoleService sysUserRoleService;


	/**
	 * æ‰€æœ‰ç”¨æˆ·åˆ—è¡¨
	 */
	@GetMapping("/list")
	@RequiresPermissions("sys:user:list")
	public R list(@RequestParam Map<String, Object> params){
		//åªæœ‰è¶…çº§ç®¡ç†å‘˜ï¼Œæ‰èƒ½æŸ¥çœ‹æ‰€æœ‰ç®¡ç†å‘˜åˆ—è¡¨
		if(getUserId() != Constant.SUPER_ADMIN){
			params.put("createUserId", getUserId());
		}
		PageUtils page = sysUserService.queryPage(params);

		return R.ok().put("page", page);
	}
	
	/**
	 * èŽ·å–ç™»å½•çš„ç”¨æˆ·ä¿¡æ¯
	 */
	@GetMapping("/info")
	public R info(){
		return R.ok().put("user", getUser());
	}
	
	/**
	 * ä¿®æ”¹ç™»å½•ç”¨æˆ·å¯†ç 
	 */
	@SysLog("ä¿®æ”¹å¯†ç ")
	@PostMapping("/password")
	public R password(@RequestBody PasswordForm form){
		Assert.isBlank(form.getNewPassword(), "æ–°å¯†ç ä¸ä¸ºèƒ½ç©º");
		
		//sha256åŠ å¯†
		String password = new Sha256Hash(form.getPassword(), getUser().getSalt()).toHex();
		//sha256åŠ å¯†
		String newPassword = new Sha256Hash(form.getNewPassword(), getUser().getSalt()).toHex();
				
		//æ›´æ–°å¯†ç 
		boolean flag = sysUserService.updatePassword(getUserId(), password, newPassword);
		if(!flag){
			return R.error("åŽŸå¯†ç ä¸æ­£ç¡®");
		}
		
		return R.ok();
	}
	
	/**
	 * ç”¨æˆ·ä¿¡æ¯
	 */
	@GetMapping("/info/{userId}")
	@RequiresPermissions("sys:user:info")
	public R info(@PathVariable("userId") Long userId){
		SysUserEntity user = sysUserService.getById(userId);
		
		//èŽ·å–ç”¨æˆ·æ‰€å±žçš„è§’è‰²åˆ—è¡¨
		List<Long> roleIdList = sysUserRoleService.queryRoleIdList(userId);
		user.setRoleIdList(roleIdList);
		
		return R.ok().put("user", user);
	}
	
	/**
	 * ä¿å­˜ç”¨æˆ·
	 */
	@SysLog("ä¿å­˜ç”¨æˆ·")
	@PostMapping("/save")
	@RequiresPermissions("sys:user:save")
	public R save(@RequestBody SysUserEntity user){
		ValidatorUtils.validateEntity(user, AddGroup.class);
		
		user.setCreateUserId(getUserId());
		sysUserService.saveUser(user);
		
		return R.ok();
	}
	
	/**
	 * ä¿®æ”¹ç”¨æˆ·
	 */
	@SysLog("ä¿®æ”¹ç”¨æˆ·")
	@PostMapping("/update")
	@RequiresPermissions("sys:user:update")
	public R update(@RequestBody SysUserEntity user){
		ValidatorUtils.validateEntity(user, UpdateGroup.class);

		user.setCreateUserId(getUserId());
		sysUserService.update(user);
		
		return R.ok();
	}
	
	/**
	 * åˆ é™¤ç”¨æˆ·
	 */
	@SysLog("åˆ é™¤ç”¨æˆ·")
	@PostMapping("/delete")
	@RequiresPermissions("sys:user:delete")
	public R delete(@RequestBody Long[] userIds){
		if(ArrayUtils.contains(userIds, 1L)){
			return R.error("ç³»ç»Ÿç®¡ç†å‘˜ä¸èƒ½åˆ é™¤");
		}
		
		if(ArrayUtils.contains(userIds, getUserId())){
			return R.error("å½“å‰ç”¨æˆ·ä¸èƒ½åˆ é™¤");
		}
		
		sysUserService.deleteBatch(userIds);
		
		return R.ok();
	}
}
