/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.common.validator.group;

import javax.validation.GroupSequence;

/**
 * å®šä¹‰æ ¡éªŒé¡ºåºï¼Œå¦‚æžœAddGroupç»„å¤±è´¥ï¼Œåˆ™UpdateGroupç»„ä¸ä¼šå†æ ¡éªŒ
 *
 * @author Mark sunlightcs@gmail.com
 */
@GroupSequence({AddGroup.class, UpdateGroup.class})
public interface Group {

}
