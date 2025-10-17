/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.job.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * æµ‹è¯•å®šæ—¶ä»»åŠ¡(æ¼”ç¤ºDemoï¼Œå¯åˆ é™¤)
 *
 * testTaskä¸ºspring beançš„åç§°
 *
 * @author Mark sunlightcs@gmail.com
 */
@Component("testTask")
public class TestTask implements ITask {
	private Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public void run(String params){
		logger.debug("TestTaskå®šæ—¶ä»»åŠ¡æ­£åœ¨æ‰§è¡Œï¼Œå‚æ•°ä¸ºï¼š{}", params);
	}
}
