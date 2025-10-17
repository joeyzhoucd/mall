/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.sys.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.renren.modules.sys.entity.SysRoleMenuEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * è§’è‰²ä¸Žèœå•å¯¹åº”å…³ç³»
 *
 * @author Mark sunlightcs@gmail.com
 */
@Mapper
public interface SysRoleMenuDao extends BaseMapper<SysRoleMenuEntity> {
	
	/**
	 * æ ¹æ®è§’è‰²IDï¼ŒèŽ·å–èœå•IDåˆ—è¡¨
	 */
	List<Long> queryMenuIdList(Long roleId);

	/**
	 * æ ¹æ®è§’è‰²IDæ•°ç»„ï¼Œæ‰¹é‡åˆ é™¤
	 */
	int deleteBatch(Long[] roleIds);
}
