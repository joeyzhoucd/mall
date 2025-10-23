

package io.renren.modules.sys.service;

import com.baomidou.mybatisplus.extension.service.IService;
import io.renren.modules.sys.entity.SysRoleMenuEntity;

import java.util.List;




public interface SysRoleMenuService extends IService<SysRoleMenuEntity> {
	
	void saveOrUpdate(Long roleId, List<Long> menuIdList);
	
	
	List<Long> queryMenuIdList(Long roleId);

	
	int deleteBatch(Long[] roleIds);
	
}
