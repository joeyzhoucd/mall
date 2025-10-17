/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

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
 * è§’è‰²
 *
 * @author Mark sunlightcs@gmail.com
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

        //æ£€æŸ¥æƒé™æ˜¯å¦è¶Šæƒ
        checkPrems(role);

        //ä¿å­˜è§’è‰²ä¸Žèœå•å…³ç³»
        sysRoleMenuService.saveOrUpdate(role.getRoleId(), role.getMenuIdList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysRoleEntity role) {
        this.updateById(role);

        //æ£€æŸ¥æƒé™æ˜¯å¦è¶Šæƒ
        checkPrems(role);

        //æ›´æ–°è§’è‰²ä¸Žèœå•å…³ç³»
        sysRoleMenuService.saveOrUpdate(role.getRoleId(), role.getMenuIdList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(Long[] roleIds) {
        //åˆ é™¤è§’è‰²
        this.removeByIds(Arrays.asList(roleIds));

        //åˆ é™¤è§’è‰²ä¸Žèœå•å…³è”
        sysRoleMenuService.deleteBatch(roleIds);

        //åˆ é™¤è§’è‰²ä¸Žç”¨æˆ·å…³è”
        sysUserRoleService.deleteBatch(roleIds);
    }


    @Override
	public List<Long> queryRoleIdList(Long createUserId) {
		return baseMapper.queryRoleIdList(createUserId);
	}

	/**
	 * æ£€æŸ¥æƒé™æ˜¯å¦è¶Šæƒ
	 */
	private void checkPrems(SysRoleEntity role){
		//å¦‚æžœä¸æ˜¯è¶…çº§ç®¡ç†å‘˜ï¼Œåˆ™éœ€è¦åˆ¤æ–­è§’è‰²çš„æƒé™æ˜¯å¦è¶…è¿‡è‡ªå·±çš„æƒé™
		if(role.getCreateUserId() == Constant.SUPER_ADMIN){
			return ;
		}
		
		//æŸ¥è¯¢ç”¨æˆ·æ‰€æ‹¥æœ‰çš„èœå•åˆ—è¡¨
		List<Long> menuIdList = sysUserDao.queryAllMenuId(role.getCreateUserId());
		
		//åˆ¤æ–­æ˜¯å¦è¶Šæƒ
		if(!menuIdList.containsAll(role.getMenuIdList())){
			throw new RRException("æ–°å¢žè§’è‰²çš„æƒé™ï¼Œå·²è¶…å‡ºä½ çš„æƒé™èŒƒå›´");
		}
	}
}
