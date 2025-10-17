/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.app.form;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * æ³¨å†Œè¡¨å•
 *
 * @author Mark sunlightcs@gmail.com
 */
@Data
@ApiModel(value = "æ³¨å†Œè¡¨å•")
public class RegisterForm {
    @ApiModelProperty(value = "æ‰‹æœºå·")
    @NotBlank(message="æ‰‹æœºå·ä¸èƒ½ä¸ºç©º")
    private String mobile;

    @ApiModelProperty(value = "å¯†ç ")
    @NotBlank(message="å¯†ç ä¸èƒ½ä¸ºç©º")
    private String password;

}
