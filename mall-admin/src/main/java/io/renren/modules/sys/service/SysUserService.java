/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.sys.service;

import com.baomidou.mybatisplus.extension.service.IService;
import io.renren.common.utils.PageUtils;
import io.renren.modules.sys.entity.SysUserEntity;

import java.util.List;
import java.util.Map;


/**
 * ç³»ç»Ÿç”¨æˆ·
 *
 * @author Mark sunlightcs@gmail.com
 */
public interface SysUserService extends IService<SysUserEntity> {

	PageUtils queryPage(Map<String, Object> params);

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

	/**
	 * ä¿å­˜ç”¨æˆ·
	 */
	void saveUser(SysUserEntity user);
	
	/**
	 * ä¿®æ”¹ç”¨æˆ·
	 */
	void update(SysUserEntity user);
	
	/**
	 * åˆ é™¤ç”¨æˆ·
	 */
	void deleteBatch(Long[] userIds);

	/**
	 * ä¿®æ”¹å¯†ç 
	 * @param userId       ç”¨æˆ·ID
	 * @param password     åŽŸå¯†ç 
	 * @param newPassword  æ–°å¯†ç 
	 */
	boolean updatePassword(Long userId, String password, String newPassword);
}
