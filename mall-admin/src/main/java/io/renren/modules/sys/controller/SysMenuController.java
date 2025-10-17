/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

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
 * ç³»ç»Ÿèœå•
 *
 * @author Mark sunlightcs@gmail.com
 */
@RestController
@RequestMapping("/sys/menu")
public class SysMenuController extends AbstractController {
	@Autowired
	private SysMenuService sysMenuService;
	@Autowired
	private ShiroService shiroService;

	/**
	 * å¯¼èˆªèœå•
	 */
	@GetMapping("/nav")
	public R nav(){
		List<SysMenuEntity> menuList = sysMenuService.getUserMenuList(getUserId());
		Set<String> permissions = shiroService.getUserPermissions(getUserId());
		return R.ok().put("menuList", menuList).put("permissions", permissions);
	}
	
	/**
	 * æ‰€æœ‰èœå•åˆ—è¡¨
	 */
	@GetMapping("/list")
	@RequiresPermissions("sys:menu:list")
	public List<SysMenuEntity> list(){
		List<SysMenuEntity> menuList = sysMenuService.list();

		//æŸ¥è¯¢å®Œæˆ å¯¹æ­¤listç›´æŽ¥æŽ’åº
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
	 * é€‰æ‹©èœå•(æ·»åŠ ã€ä¿®æ”¹èœå•)
	 */
	@GetMapping("/select")
	@RequiresPermissions("sys:menu:select")
	public R select(){
		//æŸ¥è¯¢åˆ—è¡¨æ•°æ®
		List<SysMenuEntity> menuList = sysMenuService.queryNotButtonList();
		
		//æ·»åŠ é¡¶çº§èœå•
		SysMenuEntity root = new SysMenuEntity();
		root.setMenuId(0L);
		root.setName("ä¸€çº§èœå•");
		root.setParentId(-1L);
		root.setOpen(true);
		menuList.add(root);
		
		return R.ok().put("menuList", menuList);
	}
	
	/**
	 * èœå•ä¿¡æ¯
	 */
	@GetMapping("/info/{menuId}")
	@RequiresPermissions("sys:menu:info")
	public R info(@PathVariable("menuId") Long menuId){
		SysMenuEntity menu = sysMenuService.getById(menuId);
		return R.ok().put("menu", menu);
	}
	
	/**
	 * ä¿å­˜
	 */
	@SysLog("ä¿å­˜èœå•")
	@PostMapping("/save")
	@RequiresPermissions("sys:menu:save")
	public R save(@RequestBody SysMenuEntity menu){
		//æ•°æ®æ ¡éªŒ
		verifyForm(menu);
		
		sysMenuService.save(menu);
		
		return R.ok();
	}
	
	/**
	 * ä¿®æ”¹
	 */
	@SysLog("ä¿®æ”¹èœå•")
	@PostMapping("/update")
	@RequiresPermissions("sys:menu:update")
	public R update(@RequestBody SysMenuEntity menu){
		//æ•°æ®æ ¡éªŒ
		verifyForm(menu);
				
		sysMenuService.updateById(menu);
		
		return R.ok();
	}
	
	/**
	 * åˆ é™¤
	 */
	@SysLog("åˆ é™¤èœå•")
	@PostMapping("/delete/{menuId}")
	@RequiresPermissions("sys:menu:delete")
	public R delete(@PathVariable("menuId") long menuId){
		if(menuId <= 31){
			return R.error("ç³»ç»Ÿèœå•ï¼Œä¸èƒ½åˆ é™¤");
		}

		//åˆ¤æ–­æ˜¯å¦æœ‰å­èœå•æˆ–æŒ‰é’®
		List<SysMenuEntity> menuList = sysMenuService.queryListParentId(menuId);
		if(menuList.size() > 0){
			return R.error("è¯·å…ˆåˆ é™¤å­èœå•æˆ–æŒ‰é’®");
		}

		sysMenuService.delete(menuId);

		return R.ok();
	}
	
	/**
	 * éªŒè¯å‚æ•°æ˜¯å¦æ­£ç¡®
	 */
	private void verifyForm(SysMenuEntity menu){
		if(StringUtils.isBlank(menu.getName())){
			throw new RRException("èœå•åç§°ä¸èƒ½ä¸ºç©º");
		}
		
		if(menu.getParentId() == null){
			throw new RRException("ä¸Šçº§èœå•ä¸èƒ½ä¸ºç©º");
		}
		
		//èœå•
		if(menu.getType() == Constant.MenuType.MENU.getValue()){
			if(StringUtils.isBlank(menu.getUrl())){
				throw new RRException("èœå•URLä¸èƒ½ä¸ºç©º");
			}
		}
		
		//ä¸Šçº§èœå•ç±»åž‹
		int parentType = Constant.MenuType.CATALOG.getValue();
		if(menu.getParentId() != 0){
			SysMenuEntity parentMenu = sysMenuService.getById(menu.getParentId());
			parentType = parentMenu.getType();
		}
		
		//ç›®å½•ã€èœå•
		if(menu.getType() == Constant.MenuType.CATALOG.getValue() ||
				menu.getType() == Constant.MenuType.MENU.getValue()){
			if(parentType != Constant.MenuType.CATALOG.getValue()){
				throw new RRException("ä¸Šçº§èœå•åªèƒ½ä¸ºç›®å½•ç±»åž‹");
			}
			return ;
		}
		
		//æŒ‰é’®
		if(menu.getType() == Constant.MenuType.BUTTON.getValue()){
			if(parentType != Constant.MenuType.MENU.getValue()){
				throw new RRException("ä¸Šçº§èœå•åªèƒ½ä¸ºèœå•ç±»åž‹");
			}
			return ;
		}
	}
}
