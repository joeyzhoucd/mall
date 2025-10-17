/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.sys.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.renren.modules.sys.entity.SysUserEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * ç³»ç»Ÿç”¨æˆ·
 *
 * @author Mark sunlightcs@gmail.com
 */
@Mapper
public interface SysUserDao extends BaseMapper<SysUserEntity> {
	
	/**
	 * æŸ¥è¯¢ç”¨æˆ·çš„æ‰€æœ‰æƒé™
	 * @param userId  ç”¨æˆ·ID
	 */
	List<String> queryAllPerms(Long userId);
	
	/**
	 * æŸ¥è¯¢ç”¨æˆ·çš„æ‰€æœ‰èœå•ID
	 */
	List<Long> queryAllMenuId(Long userId);
	
	/**
	 * æ ¹æ®ç”¨æˆ·åï¼ŒæŸ¥è¯¢ç³»ç»Ÿç”¨æˆ·
	 */
	SysUserEntity queryByUserName(String username);

}
