package io.renren.modules.app.controller;

import io.renren.common.utils.R;
import io.renren.common.validator.ValidatorUtils;
import io.renren.modules.app.form.LoginForm;
import io.renren.modules.app.service.UserService;
import io.renren.modules.app.utils.JwtUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * APP login controller
 */
@RestController
@RequestMapping("/app")
@Api("APP login interface")
public class AppLoginController {
    @Autowired
    private UserService userService;
    @Autowired
    private JwtUtils jwtUtils;

    /**
     * Login
     */
    @PostMapping("login")
    @ApiOperation("Login")
    public R login(@RequestBody LoginForm form){
        // Data validation
        ValidatorUtils.validateEntity(form);

        // User login
        long userId = userService.login(form);

        // Generate token
        String token = jwtUtils.generateToken(userId);

        Map<String, Object> map = new HashMap<>();
        map.put("token", token);
        map.put("expire", jwtUtils.getExpire());

        return R.ok(map);
    }

}