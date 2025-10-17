/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.job.service;

import com.baomidou.mybatisplus.extension.service.IService;
import io.renren.common.utils.PageUtils;
import io.renren.modules.job.entity.ScheduleJobEntity;

import java.util.Map;

/**
 * å®šæ—¶ä»»åŠ¡
 *
 * @author Mark sunlightcs@gmail.com
 */
public interface ScheduleJobService extends IService<ScheduleJobEntity> {

	PageUtils queryPage(Map<String, Object> params);

	/**
	 * ä¿å­˜å®šæ—¶ä»»åŠ¡
	 */
	void saveJob(ScheduleJobEntity scheduleJob);
	
	/**
	 * æ›´æ–°å®šæ—¶ä»»åŠ¡
	 */
	void update(ScheduleJobEntity scheduleJob);
	
	/**
	 * æ‰¹é‡åˆ é™¤å®šæ—¶ä»»åŠ¡
	 */
	void deleteBatch(Long[] jobIds);
	
	/**
	 * æ‰¹é‡æ›´æ–°å®šæ—¶ä»»åŠ¡çŠ¶æ€
	 */
	int updateBatch(Long[] jobIds, int status);
	
	/**
	 * ç«‹å³æ‰§è¡Œ
	 */
	void run(Long[] jobIds);
	
	/**
	 * æš‚åœè¿è¡Œ
	 */
	void pause(Long[] jobIds);
	
	/**
	 * æ¢å¤è¿è¡Œ
	 */
	void resume(Long[] jobIds);
}
