/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.sys.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.renren.common.utils.Constant;
import io.renren.common.utils.MapUtils;
import io.renren.modules.sys.dao.SysMenuDao;
import io.renren.modules.sys.entity.SysMenuEntity;
import io.renren.modules.sys.service.SysMenuService;
import io.renren.modules.sys.service.SysRoleMenuService;
import io.renren.modules.sys.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;


@Service("sysMenuService")
public class SysMenuServiceImpl extends ServiceImpl<SysMenuDao, SysMenuEntity> implements SysMenuService {
	@Autowired
	private SysUserService sysUserService;
	@Autowired
	private SysRoleMenuService sysRoleMenuService;
	
	@Override
	public List<SysMenuEntity> queryListParentId(Long parentId, List<Long> menuIdList) {
		List<SysMenuEntity> menuList = queryListParentId(parentId);
		if(menuIdList == null){
			return menuList;
		}
		
		List<SysMenuEntity> userMenuList = new ArrayList<>();
		for(SysMenuEntity menu : menuList){
			if(menuIdList.contains(menu.getMenuId())){
				userMenuList.add(menu);
			}
		}
		return userMenuList;
	}

	@Override
	public List<SysMenuEntity> queryListParentId(Long parentId) {
		return baseMapper.queryListParentId(parentId);
	}

	@Override
	public List<SysMenuEntity> queryNotButtonList() {
		return baseMapper.queryNotButtonList();
	}

	@Override
	public List<SysMenuEntity> getUserMenuList(Long userId) {
		//ç³»ç»Ÿç®¡ç†å‘˜ï¼Œæ‹¥æœ‰æœ€é«˜æƒé™
		if(userId == Constant.SUPER_ADMIN){
			return getMenuList(null);
		}
		
		//ç”¨æˆ·èœå•åˆ—è¡¨
		List<Long> menuIdList = sysUserService.queryAllMenuId(userId);
		return getMenuList(menuIdList);
	}

	/**
	 * èŽ·å–æ‹¥æœ‰çš„èœå•åˆ—è¡¨
	 * @param menuIdList
	 * @return
	 */
	private List<SysMenuEntity> getMenuList(List<Long> menuIdList) {
		// æŸ¥è¯¢æ‹¥æœ‰çš„æ‰€æœ‰èœå•
		List<SysMenuEntity> menus = this.baseMapper.selectList(new QueryWrapper<SysMenuEntity>()
				.in(Objects.nonNull(menuIdList), "menu_id", menuIdList).in("type", 0, 1));
		//æŸ¥è¯¢å®Œæˆ å¯¹æ­¤listç›´æŽ¥æŽ’åº
		Collections.sort(menus);

		// å°†idå’Œèœå•ç»‘å®š
		HashMap<Long, SysMenuEntity> menuMap = new HashMap<>(12);
		for (SysMenuEntity s : menus) {
			menuMap.put(s.getMenuId(), s);
		}
		// ä½¿ç”¨è¿­ä»£å™¨,ç»„è£…èœå•çš„å±‚çº§å…³ç³»
		Iterator<SysMenuEntity> iterator = menus.iterator();
		while (iterator.hasNext()) {
			SysMenuEntity menu = iterator.next();
			SysMenuEntity parent = menuMap.get(menu.getParentId());
			if (Objects.nonNull(parent)) {
				parent.getList().add(menu);
				// å°†è¿™ä¸ªèœå•ä»Žå½“å‰èŠ‚ç‚¹ç§»é™¤
				iterator.remove();
			}
		}

		return menus;
	}

	@Override
	public void delete(Long menuId){
		//åˆ é™¤èœå•
		this.removeById(menuId);
		//åˆ é™¤èœå•ä¸Žè§’è‰²å…³è”
		sysRoleMenuService.removeByMap(new MapUtils().put("menu_id", menuId));
	}

	/**
	 * èŽ·å–æ‰€æœ‰èœå•åˆ—è¡¨
	 */
	private List<SysMenuEntity> getAllMenuList(List<Long> menuIdList){
		//æŸ¥è¯¢æ ¹èœå•åˆ—è¡¨
		List<SysMenuEntity> menuList = queryListParentId(0L, menuIdList);
		//é€’å½’èŽ·å–å­èœå•
		getMenuTreeList(menuList, menuIdList);
		
		return menuList;
	}

	/**
	 * é€’å½’
	 */
	private List<SysMenuEntity> getMenuTreeList(List<SysMenuEntity> menuList, List<Long> menuIdList){
		List<SysMenuEntity> subMenuList = new ArrayList<SysMenuEntity>();
		
		for(SysMenuEntity entity : menuList){
			//ç›®å½•
			if(entity.getType() == Constant.MenuType.CATALOG.getValue()){
				entity.setList(getMenuTreeList(queryListParentId(entity.getMenuId(), menuIdList), menuIdList));
			}
			subMenuList.add(entity);
		}
		
		return subMenuList;
	}
}
