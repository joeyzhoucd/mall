package io.renren.modules.sys.controller;

import io.renren.common.annotation.SysLog;
import io.renren.common.exception.RRException;
import io.renren.common.utils.Constant;
import io.renren.common.utils.R;
import io.renren.modules.sys.entity.SysMenuEntity;
import io.renren.modules.sys.service.ShiroService;
import io.renren.modules.sys.service.SysMenuService;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * System menu controller
 */
@RestController
@RequestMapping("/sys/menu")
public class SysMenuController extends AbstractController {
	@Autowired
	private SysMenuService sysMenuService;
	@Autowired
	private ShiroService shiroService;

	/**
	 * Get navigation menu
	 */
	@GetMapping("/nav")
	public R nav(){
		List<SysMenuEntity> menuList = sysMenuService.getUserMenuList(getUserId());
		Set<String> permissions = shiroService.getUserPermissions(getUserId());
		return R.ok().put("menuList", menuList).put("permissions", permissions);
	}
	
	/**
	 * List all menus
	 */
	@GetMapping("/list")
	@RequiresPermissions("sys:menu:list")
	public List<SysMenuEntity> list(){
		List<SysMenuEntity> menuList = sysMenuService.list();

		// Sort by order
		Collections.sort(menuList);

		HashMap<Long, SysMenuEntity> menuMap = new HashMap<>(12);
		for (SysMenuEntity s : menuList) {
			menuMap.put(s.getMenuId(), s);
		}
		for (SysMenuEntity s : menuList) {
			SysMenuEntity parent = menuMap.get(s.getParentId());
			if (Objects.nonNull(parent)) {
				s.setParentName(parent.getName());
			}

		}

		return menuList;
	}
	
	/**
	 * Select menu
	 */
	@GetMapping("/select")
	@RequiresPermissions("sys:menu:select")
	public R select(){
		// Query list
		List<SysMenuEntity> menuList = sysMenuService.queryNotButtonList();
		
		// Add root menu
		SysMenuEntity root = new SysMenuEntity();
		root.setMenuId(0L);
		root.setName("Root menu");
		root.setParentId(-1L);
		root.setOpen(true);
		menuList.add(root);
		
		return R.ok().put("menuList", menuList);
	}
	
	/**
	 * Get menu info
	 */
	@GetMapping("/info/{menuId}")
	@RequiresPermissions("sys:menu:info")
	public R info(@PathVariable("menuId") Long menuId){
		SysMenuEntity menu = sysMenuService.getById(menuId);
		return R.ok().put("menu", menu);
	}
	
	/**
	 * Save menu
	 */
	@SysLog("Save menu")
	@PostMapping("/save")
	@RequiresPermissions("sys:menu:save")
	public R save(@RequestBody SysMenuEntity menu){
		// Data validation
		verifyForm(menu);
		
		sysMenuService.save(menu);
		
		return R.ok();
	}
	
	/**
	 * Update menu
	 */
	@SysLog("Update menu")
	@PostMapping("/update")
	@RequiresPermissions("sys:menu:update")
	public R update(@RequestBody SysMenuEntity menu){
		// Data validation
		verifyForm(menu);
				
		sysMenuService.updateById(menu);
		
		return R.ok();
	}
	
	/**
	 * Delete menu
	 */
	@SysLog("Delete menu")
	@PostMapping("/delete/{menuId}")
	@RequiresPermissions("sys:menu:delete")
	public R delete(@PathVariable("menuId") long menuId){
		if(menuId <= 31){
			return R.error("System menu cannot be deleted");
		}

		// Check if there are sub menus
		List<SysMenuEntity> menuList = sysMenuService.queryListParentId(menuId);
		if(menuList.size() > 0){
			return R.error("Please delete sub menus first");
		}

		sysMenuService.delete(menuId);

		return R.ok();
	}
	
	/**
	 * Verify form data
	 */
	private void verifyForm(SysMenuEntity menu){
		if(StringUtils.isBlank(menu.getName())){
			throw new RRException("Menu name cannot be empty");
		}
		
		if(menu.getParentId() == null){
			throw new RRException("Parent menu cannot be empty");
		}
		
		// Menu
		if(menu.getType() == Constant.MenuType.MENU.getValue()){
			if(StringUtils.isBlank(menu.getUrl())){
				throw new RRException("Menu URL cannot be empty");
			}
		}
		
		// Parent menu type
		int parentType = Constant.MenuType.CATALOG.getValue();
		if(menu.getParentId() != 0){
			SysMenuEntity parentMenu = sysMenuService.getById(menu.getParentId());
			parentType = parentMenu.getType();
		}
		
		// Catalog and menu
		if(menu.getType() == Constant.MenuType.CATALOG.getValue() ||
				menu.getType() == Constant.MenuType.MENU.getValue()){
			if(parentType != Constant.MenuType.CATALOG.getValue()){
				throw new RRException("Parent menu must be catalog type");
			}
			return ;
		}
		
		// Button
		if(menu.getType() == Constant.MenuType.BUTTON.getValue()){
			if(parentType != Constant.MenuType.MENU.getValue()){
				throw new RRException("Parent menu must be menu type");
			}
			return ;
		}
	}
}