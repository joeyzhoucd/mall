package io.renren.modules.app.controller;

import io.renren.common.utils.R;
import io.renren.modules.app.annotation.Login;
import io.renren.modules.app.annotation.LoginUser;
import io.renren.modules.app.entity.UserEntity;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP test controller
 */
@RestController
@RequestMapping("/app")
@Api("APP test interface")
public class AppTestController {

    /**
     * Get user info
     */
    @Login
    @GetMapping("userInfo")
    @ApiOperation("Get user info")
    public R userInfo(@LoginUser UserEntity user){
        return R.ok().put("user", user);
    }

    /**
     * Get user ID
     */
    @Login
    @GetMapping("userId")
    @ApiOperation("Get user ID")
    public R userInfo(@RequestAttribute("userId") Integer userId){
        return R.ok().put("userId", userId);
    }

    /**
     * Test without token
     */
    @GetMapping("notToken")
    @ApiOperation("Test without token")
    public R notToken(){
        return R.ok().put("msg", "No token required for this interface");
    }

}