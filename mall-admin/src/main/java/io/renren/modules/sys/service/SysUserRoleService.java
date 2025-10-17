/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.sys.service;

import com.baomidou.mybatisplus.extension.service.IService;
import io.renren.modules.sys.entity.SysUserRoleEntity;

import java.util.List;



/**
 * ç”¨æˆ·ä¸Žè§’è‰²å¯¹åº”å…³ç³»
 *
 * @author Mark sunlightcs@gmail.com
 */
public interface SysUserRoleService extends IService<SysUserRoleEntity> {
	
	void saveOrUpdate(Long userId, List<Long> roleIdList);
	
	/**
	 * æ ¹æ®ç”¨æˆ·IDï¼ŒèŽ·å–è§’è‰²IDåˆ—è¡¨
	 */
	List<Long> queryRoleIdList(Long userId);

	/**
	 * æ ¹æ®è§’è‰²IDæ•°ç»„ï¼Œæ‰¹é‡åˆ é™¤
	 */
	int deleteBatch(Long[] roleIds);
}
