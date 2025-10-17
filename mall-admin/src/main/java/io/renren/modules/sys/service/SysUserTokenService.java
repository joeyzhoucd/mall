/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.sys.service;

import com.baomidou.mybatisplus.extension.service.IService;
import io.renren.common.utils.R;
import io.renren.modules.sys.entity.SysUserTokenEntity;

/**
 * ç”¨æˆ·Token
 *
 * @author Mark sunlightcs@gmail.com
 */
public interface SysUserTokenService extends IService<SysUserTokenEntity> {

	/**
	 * ç”Ÿæˆtoken
	 * @param userId  ç”¨æˆ·ID
	 */
	R createToken(long userId);

	/**
	 * é€€å‡ºï¼Œä¿®æ”¹tokenå€¼
	 * @param userId  ç”¨æˆ·ID
	 */
	void logout(long userId);

}
