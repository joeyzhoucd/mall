/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.sys.service;

import com.baomidou.mybatisplus.extension.service.IService;
import io.renren.modules.sys.entity.SysRoleMenuEntity;

import java.util.List;



/**
 * è§’è‰²ä¸Žèœå•å¯¹åº”å…³ç³»
 *
 * @author Mark sunlightcs@gmail.com
 */
public interface SysRoleMenuService extends IService<SysRoleMenuEntity> {
	
	void saveOrUpdate(Long roleId, List<Long> menuIdList);
	
	/**
	 * æ ¹æ®è§’è‰²IDï¼ŒèŽ·å–èœå•IDåˆ—è¡¨
	 */
	List<Long> queryMenuIdList(Long roleId);

	/**
	 * æ ¹æ®è§’è‰²IDæ•°ç»„ï¼Œæ‰¹é‡åˆ é™¤
	 */
	int deleteBatch(Long[] roleIds);
	
}
