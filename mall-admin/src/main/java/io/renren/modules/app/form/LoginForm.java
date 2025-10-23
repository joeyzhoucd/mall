package io.renren.modules.app.form;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * Login form
 */
@Data
@ApiModel(value = "Login form")
public class LoginForm {
    @ApiModelProperty(value = "Mobile number")
    @NotBlank(message="Mobile number cannot be empty")
    private String mobile;

    @ApiModelProperty(value = "Password")
    @NotBlank(message="Password cannot be empty")
    private String password;

}