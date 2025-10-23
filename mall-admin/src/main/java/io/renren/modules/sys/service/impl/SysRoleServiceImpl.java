package io.renren.modules.sys.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.renren.common.exception.RRException;
import io.renren.common.utils.Constant;
import io.renren.common.utils.PageUtils;
import io.renren.common.utils.Query;
import io.renren.modules.sys.dao.SysRoleDao;
import io.renren.modules.sys.dao.SysUserDao;
import io.renren.modules.sys.entity.SysRoleEntity;
import io.renren.modules.sys.service.SysRoleMenuService;
import io.renren.modules.sys.service.SysRoleService;
import io.renren.modules.sys.service.SysUserRoleService;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * System role service implementation
 */
@Service("sysRoleService")
public class SysRoleServiceImpl extends ServiceImpl<SysRoleDao, SysRoleEntity> implements SysRoleService {
	@Autowired
	private SysRoleMenuService sysRoleMenuService;
	@Autowired
	private SysUserDao sysUserDao;
    @Autowired
    private SysUserRoleService sysUserRoleService;

	@Override
	public PageUtils queryPage(Map<String, Object> params) {
		String roleName = (String)params.get("roleName");
		Long createUserId = (Long)params.get("createUserId");

		IPage<SysRoleEntity> page = this.page(
			new Query<SysRoleEntity>().getPage(params),
			new QueryWrapper<SysRoleEntity>()
				.like(StringUtils.isNotBlank(roleName),"role_name", roleName)
				.eq(createUserId != null,"create_user_id", createUserId)
		);

		return new PageUtils(page);
	}

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRole(SysRoleEntity role) {
        role.setCreateTime(new Date());
        this.save(role);

        // Check role permissions
        checkPrems(role);

        // Save role menu relations
        sysRoleMenuService.saveOrUpdate(role.getRoleId(), role.getMenuIdList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysRoleEntity role) {
        this.updateById(role);

        // Check role permissions
        checkPrems(role);

        // Update role menu relations
        sysRoleMenuService.saveOrUpdate(role.getRoleId(), role.getMenuIdList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(Long[] roleIds) {
        // Delete roles
        this.removeByIds(Arrays.asList(roleIds));

        // Delete role menu relations
        sysRoleMenuService.deleteBatch(roleIds);

        // Delete role user relations
        sysUserRoleService.deleteBatch(roleIds);
    }

    @Override
	public List<Long> queryRoleIdList(Long createUserId) {
		return baseMapper.queryRoleIdList(createUserId);
	}

	/**
	 * Check role permissions
	 */
	private void checkPrems(SysRoleEntity role){
		// Super admin has all permissions
		if(role.getCreateUserId() == Constant.SUPER_ADMIN){
			return ;
		}
		
		// Query user menu list
		List<Long> menuIdList = sysUserDao.queryAllMenuId(role.getCreateUserId());
		
		// Check permissions
		if(!menuIdList.containsAll(role.getMenuIdList())){
			throw new RRException("New role permissions cannot exceed your own permissions");
		}
	}
}