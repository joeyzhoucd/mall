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
 * System user controller
 */
@RestController
@RequestMapping("/sys/user")
public class SysUserController extends AbstractController {
	@Autowired
	private SysUserService sysUserService;
	@Autowired
	private SysUserRoleService sysUserRoleService;

	/**
	 * List users
	 */
	@GetMapping("/list")
	@RequiresPermissions("sys:user:list")
	public R list(@RequestParam Map<String, Object> params){
		// Super admin can see all users
		if(getUserId() != Constant.SUPER_ADMIN){
			params.put("createUserId", getUserId());
		}
		PageUtils page = sysUserService.queryPage(params);

		return R.ok().put("page", page);
	}
	
	/**
	 * Get current user info
	 */
	@GetMapping("/info")
	public R info(){
		return R.ok().put("user", getUser());
	}
	
	/**
	 * Update password
	 */
	@SysLog("Update password")
	@PostMapping("/password")
	public R password(@RequestBody PasswordForm form){
		Assert.isBlank(form.getNewPassword(), "New password cannot be empty");
		
		// sha256 encryption
		String password = new Sha256Hash(form.getPassword(), getUser().getSalt()).toHex();
		// sha256 encryption
		String newPassword = new Sha256Hash(form.getNewPassword(), getUser().getSalt()).toHex();
				
		// Update password
		boolean flag = sysUserService.updatePassword(getUserId(), password, newPassword);
		if(!flag){
			return R.error("Old password error");
		}
		
		return R.ok();
	}
	
	/**
	 * Get user info
	 */
	@GetMapping("/info/{userId}")
	@RequiresPermissions("sys:user:info")
	public R info(@PathVariable("userId") Long userId){
		SysUserEntity user = sysUserService.getById(userId);
		
		// Query user role list
		List<Long> roleIdList = sysUserRoleService.queryRoleIdList(userId);
		user.setRoleIdList(roleIdList);
		
		return R.ok().put("user", user);
	}
	
	/**
	 * Save user
	 */
	@SysLog("Save user")
	@PostMapping("/save")
	@RequiresPermissions("sys:user:save")
	public R save(@RequestBody SysUserEntity user){
		ValidatorUtils.validateEntity(user, AddGroup.class);
		
		user.setCreateUserId(getUserId());
		sysUserService.saveUser(user);
		
		return R.ok();
	}
	
	/**
	 * Update user
	 */
	@SysLog("Update user")
	@PostMapping("/update")
	@RequiresPermissions("sys:user:update")
	public R update(@RequestBody SysUserEntity user){
		ValidatorUtils.validateEntity(user, UpdateGroup.class);

		user.setCreateUserId(getUserId());
		sysUserService.update(user);
		
		return R.ok();
	}
	
	/**
	 * Delete user
	 */
	@SysLog("Delete user")
	@PostMapping("/delete")
	@RequiresPermissions("sys:user:delete")
	public R delete(@RequestBody Long[] userIds){
		if(ArrayUtils.contains(userIds, 1L)){
			return R.error("Super admin cannot be deleted");
		}
		
		if(ArrayUtils.contains(userIds, getUserId())){
			return R.error("Current user cannot be deleted");
		}
		
		sysUserService.deleteBatch(userIds);
		
		return R.ok();
	}
}