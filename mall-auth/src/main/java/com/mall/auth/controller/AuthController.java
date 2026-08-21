package com.mall.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.auth.feign.MemberFeignService;
import com.mall.auth.vo.UserLoginVo;
import com.mall.auth.vo.UserRegistVo;
import com.mall.common.utils.R;
import com.mall.session.vo.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Controller
public class AuthController {

    @Autowired
    private MemberFeignService memberFeignService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping("/register.html")
    public String regPage() {
        return "register";
    }

    @GetMapping("/login.html")
    public String loginPage(HttpSession session, org.springframework.ui.Model model) {
        Object attribute = session.getAttribute("loginUser");
        if (attribute != null) {
            // 已登录用户展示信息，提供“退出/返回首页”入口，而不是直接重定向
            model.addAttribute("currentUser", attribute);
        }
        return "login";
    }

    @GetMapping("/login")
    public String loginIndex() {
        return "redirect:http://auth.mall.com/login.html";
    }

    @GetMapping("/register")
    public String registerIndex() {
        return "redirect:http://auth.mall.com/register.html";
    }

    @ResponseBody
    @GetMapping("/sms/sendcode")
    public R sendCode(@RequestParam("phone") String phone) {
        // 1. Check for anti-spam (prevent frequent requests)
        String redisCode = redisTemplate.opsForValue().get("sms:code:" + phone);
        if (!StringUtils.isEmpty(redisCode)) {
            long l = Long.parseLong(redisCode.split("_")[1]);
            if (System.currentTimeMillis() - l < 60000) {
                // Less than 60 seconds since last send
                return R.error(10002, "短信验证码发送频率过高，请稍后再试");
            }
        }

        // 2. Generate 6-digit code (or 4-digit as before)
        String code = String.valueOf((int) ((Math.random() * 9 + 1) * 1000));

        // 3. Save to Redis with timestamp to handle anti-spam: code_timestamp
        log.info("Mock SMS Send: Phone={}, Code={}", phone, code);
        String redisValue = code + "_" + System.currentTimeMillis();
        redisTemplate.opsForValue().set("sms:code:" + phone, redisValue, 10, TimeUnit.MINUTES);

        // 纯 mock 接口，没有真实短信通道，直接把验证码回显给前端方便测试
        return R.ok().put("smsCode", code);
    }

    @PostMapping("/register")
    public String register(@Valid UserRegistVo vo, BindingResult result, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            Map<String, String> errors = result.getFieldErrors().stream()
                    .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage));
            redirectAttributes.addFlashAttribute("errors", errors);
            return "redirect:http://auth.mall.com/register.html";
        }

        // 1. Verify Code from Redis
        String code = vo.getCode();
        String redisCode = redisTemplate.opsForValue().get("sms:code:" + vo.getPhone());

        if (!StringUtils.isEmpty(redisCode)) {
            if (code.equals(redisCode.split("_")[0])) {
                // Verification successful, delete token
                redisTemplate.delete("sms:code:" + vo.getPhone());

                // 2. Call Member Service to register
                try {
                    R r = memberFeignService.register(vo);
                    if (r.getCode() == 0) {
                        return "redirect:http://auth.mall.com/login.html";
                    } else {
                        Map<String, String> errors = new HashMap<>();
                        errors.put("msg", (String) r.get("msg"));
                        redirectAttributes.addFlashAttribute("errors", errors);
                        return "redirect:http://auth.mall.com/register.html";
                    }
                } catch (Exception e) {
                    Map<String, String> errors = new HashMap<>();
                    errors.put("msg", "注册服务暂时不可用，请稍后再试: " + e.getMessage());
                    redirectAttributes.addFlashAttribute("errors", errors);
                    return "redirect:http://auth.mall.com/register.html";
                }
            } else {
                Map<String, String> errors = new HashMap<>();
                errors.put("code", "验证码错误");
                redirectAttributes.addFlashAttribute("errors", errors);
                return "redirect:http://auth.mall.com/register.html";
            }
        } else {
            Map<String, String> errors = new HashMap<>();
            errors.put("code", "验证码已过期");
            redirectAttributes.addFlashAttribute("errors", errors);
            return "redirect:http://auth.mall.com/register.html";
        }
    }

    @PostMapping("/login")
    public String login(UserLoginVo vo, RedirectAttributes redirectAttributes, HttpSession session, HttpServletRequest request) {
        try {
            R r = memberFeignService.login(vo);
            if (r.getCode() == 0) {
                // Login success - renew session id to prevent fixation
                request.changeSessionId();
                LoginUser loginUser = objectMapper.convertValue(r.get("member"), LoginUser.class);
                session.setAttribute("loginUser", loginUser); // Store user info (already脱敏)
                return "redirect:http://mall.com";
            } else {
                Map<String, String> errors = new HashMap<>();
                errors.put("msg", "账号或密码错误");
                redirectAttributes.addFlashAttribute("errors", errors);
                return "redirect:http://auth.mall.com/login.html";
            }
        } catch (Exception e) {
            Map<String, String> errors = new HashMap<>();
            errors.put("msg", "登录服务暂时不可用，请稍后再试");
            redirectAttributes.addFlashAttribute("errors", errors);
            return "redirect:http://auth.mall.com/login.html";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:http://auth.mall.com/login.html";
    }
}
