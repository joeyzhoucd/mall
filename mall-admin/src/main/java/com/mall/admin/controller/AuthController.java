package com.mall.admin.controller;

import com.mall.admin.entity.SysUserEntity;
import com.mall.admin.security.JwtService;
import com.mall.admin.service.CaptchaService;
import com.mall.admin.service.SysUserService;
import com.mall.common.utils.R;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;

/**
 * 登录、登出、验证码。
 */
@RestController
public class AuthController {

    private final CaptchaService captchaService;
    private final SysUserService sysUserService;
    private final JwtService jwtService;

    public AuthController(CaptchaService captchaService, SysUserService sysUserService, JwtService jwtService) {
        this.captchaService = captchaService;
        this.sysUserService = sysUserService;
        this.jwtService = jwtService;
    }

    /**
     * 验证码图片。
     * <p>
     * 路径是根下的 /captcha.jpg（不在 /sys 下面），因为前端是这么拼的：
     * {@code this.captchaPath = adornUrl('/captcha.jpg?uuid=' + uuid)}。
     * 内容实际是 PNG —— 后缀 .jpg 只是前端写死的 URL，浏览器按 Content-Type 解析，不影响显示。
     * 保持 URL 不变是为了不改前端。
     */
    @GetMapping("/captcha.jpg")
    public void captcha(@RequestParam("uuid") String uuid, HttpServletResponse response) throws IOException {
        // 禁止缓存：否则点"看不清换一张"时浏览器可能直接给出缓存里的旧图，
        // 而服务端的答案已经换了，用户怎么填都错。
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setContentType(MediaType.IMAGE_PNG_VALUE);
        BufferedImage image = captchaService.create(uuid);
        try (OutputStream out = response.getOutputStream()) {
            ImageIO.write(image, "png", out);
        }
    }

    /**
     * 登录。
     * <p>
     * 失败一律回 code=500 加 msg（前端会把 msg 直接弹出来）。注意即使是"没登录成功"，
     * HTTP 状态码也必须是 200 —— 前端只看 body 里的 code。
     * <p>
     * 失败提示刻意不区分"用户不存在"和"密码错误"，统一说"用户名或密码错误"：
     * 区分开等于给爆破提供了一个可用的用户名枚举接口。
     */
    @PostMapping("/sys/login")
    public R login(@RequestBody LoginForm form) {
        if (!captchaService.verify(form.uuid(), form.captcha())) {
            return R.error("验证码不正确");
        }
        SysUserEntity user = sysUserService.findByUsername(form.username());
        if (!sysUserService.matchesPassword(user, form.password())) {
            return R.error("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != SysUserService.STATUS_ENABLED) {
            return R.error("账号已被锁定，请联系管理员");
        }
        String token = jwtService.issue(user.getUserId(), user.getUsername());
        // expire 字段前端目前不用，返回它是为了和旧响应形状一致，减少前端将来改动的可能。
        return R.ok().put("token", token).put("expire", jwtService.expireSeconds());
    }

    /**
     * 登出。
     * <p>
     * JWT 无状态，服务端没有会话可以销毁，所以这里只是回一个成功让前端清 cookie。
     * 令牌本身要等过期才真正失效 —— 这是选择无状态令牌的已知代价，
     * 在 application.yml 里把有效期设得比较短来缓解。
     * 要做到"登出即失效"需要一个 Redis 黑名单，当前没做。
     */
    @PostMapping("/sys/logout")
    public R logout() {
        return R.ok();
    }

    /**
     * 登录表单。
     * <p>
     * 四个字段都是前端必填的（含验证码，见 login.vue 的表单校验），
     * 所以这里也全部要求非空。
     */
    public record LoginForm(@NotBlank String username,
                            @NotBlank String password,
                            @NotBlank String uuid,
                            @NotBlank String captcha) {
    }
}
