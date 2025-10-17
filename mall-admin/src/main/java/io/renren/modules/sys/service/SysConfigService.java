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
import io.renren.modules.sys.entity.SysConfigEntity;

import java.util.Map;

/**
 * ç³»ç»Ÿé…ç½®ä¿¡æ¯
 *
 * @author Mark sunlightcs@gmail.com
 */
public interface SysConfigService extends IService<SysConfigEntity> {

	PageUtils queryPage(Map<String, Object> params);
	
	/**
	 * ä¿å­˜é…ç½®ä¿¡æ¯
	 */
	public void saveConfig(SysConfigEntity config);
	
	/**
	 * æ›´æ–°é…ç½®ä¿¡æ¯
	 */
	public void update(SysConfigEntity config);
	
	/**
	 * æ ¹æ®keyï¼Œæ›´æ–°value
	 */
	public void updateValueByKey(String key, String value);
	
	/**
	 * åˆ é™¤é…ç½®ä¿¡æ¯
	 */
	public void deleteBatch(Long[] ids);
	
	/**
	 * æ ¹æ®keyï¼ŒèŽ·å–é…ç½®çš„valueå€¼
	 * 
	 * @param key           key
	 */
	public String getValue(String key);
	
	/**
	 * æ ¹æ®keyï¼ŒèŽ·å–valueçš„Objectå¯¹è±¡
	 * @param key    key
	 * @param clazz  Objectå¯¹è±¡
	 */
	public <T> T getConfigObject(String key, Class<T> clazz);
	
}
