/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.job.task;

/**
 * å®šæ—¶ä»»åŠ¡æŽ¥å£ï¼Œæ‰€æœ‰å®šæ—¶ä»»åŠ¡éƒ½è¦å®žçŽ°è¯¥æŽ¥å£
 *
 * @author Mark sunlightcs@gmail.com
 */
public interface ITask {

    /**
     * æ‰§è¡Œå®šæ—¶ä»»åŠ¡æŽ¥å£
     *
     * @param params   å‚æ•°ï¼Œå¤šå‚æ•°ä½¿ç”¨JSONæ•°æ®
     */
    void run(String params);
}
