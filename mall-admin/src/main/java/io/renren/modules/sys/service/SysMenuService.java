

package io.renren.modules.sys.service;


import com.baomidou.mybatisplus.extension.service.IService;
import io.renren.modules.sys.entity.SysMenuEntity;

import java.util.List;



public interface SysMenuService extends IService<SysMenuEntity> {

	
	List<SysMenuEntity> queryListParentId(Long parentId, List<Long> menuIdList);

	
	List<SysMenuEntity> queryListParentId(Long parentId);
	
	
	List<SysMenuEntity> queryNotButtonList();
	
	
	List<SysMenuEntity> getUserMenuList(Long userId);

	
	void delete(Long menuId);
}
