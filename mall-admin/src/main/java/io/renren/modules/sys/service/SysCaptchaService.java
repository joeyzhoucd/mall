/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.sys.service;

import com.baomidou.mybatisplus.extension.service.IService;
import io.renren.modules.sys.entity.SysCaptchaEntity;

import java.awt.image.BufferedImage;

/**
 * éªŒè¯ç 
 *
 * @author Mark sunlightcs@gmail.com
 */
public interface SysCaptchaService extends IService<SysCaptchaEntity> {

    /**
     * èŽ·å–å›¾ç‰‡éªŒè¯ç 
     */
    BufferedImage getCaptcha(String uuid);

    /**
     * éªŒè¯ç æ•ˆéªŒ
     * @param uuid  uuid
     * @param code  éªŒè¯ç 
     * @return  trueï¼šæˆåŠŸ  falseï¼šå¤±è´¥
     */
    boolean validate(String uuid, String code);
}
