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

/**
 * System menu service implementation
 */
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
		// Super admin has all menu permissions
		if(userId == Constant.SUPER_ADMIN){
			return getMenuList(null);
		}
		
		// User menu list
		List<Long> menuIdList = sysUserService.queryAllMenuId(userId);
		return getMenuList(menuIdList);
	}

	/**
	 * Get menu list
	 */
	private List<SysMenuEntity> getMenuList(List<Long> menuIdList) {
		// Query all menus
		List<SysMenuEntity> menus = this.baseMapper.selectList(new QueryWrapper<SysMenuEntity>()
				.in(Objects.nonNull(menuIdList), "menu_id", menuIdList).in("type", 0, 1));
		// Sort by order
		Collections.sort(menus);

		// Build menu map
		HashMap<Long, SysMenuEntity> menuMap = new HashMap<>(12);
		for (SysMenuEntity s : menus) {
			menuMap.put(s.getMenuId(), s);
		}
		// Build tree structure
		Iterator<SysMenuEntity> iterator = menus.iterator();
		while (iterator.hasNext()) {
			SysMenuEntity menu = iterator.next();
			SysMenuEntity parent = menuMap.get(menu.getParentId());
			if (Objects.nonNull(parent)) {
				parent.getList().add(menu);
				// Remove child menu from root list
				iterator.remove();
			}
		}

		return menus;
	}

	@Override
	public void delete(Long menuId){
		// Delete menu
		this.removeById(menuId);
		// Delete menu role relations
		sysRoleMenuService.removeByMap(new MapUtils().put("menu_id", menuId));
	}

	/**
	 * Get all menu list
	 */
	private List<SysMenuEntity> getAllMenuList(List<Long> menuIdList){
		// Query menu list
		List<SysMenuEntity> menuList = queryListParentId(0L, menuIdList);
		// Recursively get sub menus
		getMenuTreeList(menuList, menuIdList);
		
		return menuList;
	}

	/**
	 * Get menu tree list
	 */
	private List<SysMenuEntity> getMenuTreeList(List<SysMenuEntity> menuList, List<Long> menuIdList){
		List<SysMenuEntity> subMenuList = new ArrayList<SysMenuEntity>();
		
		for(SysMenuEntity entity : menuList){
			// Catalog
			if(entity.getType() == Constant.MenuType.CATALOG.getValue()){
				entity.setList(getMenuTreeList(queryListParentId(entity.getMenuId(), menuIdList), menuIdList));
			}
			subMenuList.add(entity);
		}
		
		return subMenuList;
	}
}