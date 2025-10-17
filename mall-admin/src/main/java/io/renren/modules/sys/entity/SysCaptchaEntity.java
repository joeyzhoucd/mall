/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.sys.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * ç³»ç»ŸéªŒè¯ç 
 *
 * @author Mark sunlightcs@gmail.com
 */
@Data
@TableName("sys_captcha")
public class SysCaptchaEntity {
    @TableId(type = IdType.INPUT)
    private String uuid;
    /**
     * éªŒè¯ç 
     */
    private String code;
    /**
     * è¿‡æœŸæ—¶é—´
     */
    private Date expireTime;

}
