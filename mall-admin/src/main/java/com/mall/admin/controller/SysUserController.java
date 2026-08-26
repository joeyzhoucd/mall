package com.mall.admin.controller;

import com.mall.admin.entity.SysUserEntity;
import com.mall.admin.security.JwtService;
import com.mall.admin.service.SysUserService;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 后台用户管理。
 */
@RestController
@RequestMapping("/sys/user")
public class SysUserController {

    private final SysUserService sysUserService;

    public SysUserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @GetMapping("/list")
    public R list(@RequestParam(value = "page", defaultValue = "1") int page,
                  @RequestParam(value = "limit", defaultValue = "10") int limit,
                  @RequestParam(value = "username", required = false) String username) {
        // PageUtils 的字段正好是前端要的 totalCount/pageSize/totalPage/currPage/list，
        // 而且 list 在 page 对象【里面】而不是响应顶层，与契约一致。
        return R.ok().put("page", new PageUtils(sysUserService.page(page, limit, username)));
    }

    /**
     * 当前登录用户信息。主框架 main.vue 加载时调，只用到 userId 和 username。
     * <p>
     * 注意这个路径没有参数，和下面带 userId 的是两个不同接口。
     */
    @GetMapping("/info")
    public R currentInfo() {
        JwtService.LoginUser user = CurrentUser.get();
        if (user == null) {
            return R.error("未登录");
        }
        return R.ok().put("user", sysUserService.infoWithRoles(user.userId()));
    }

    @GetMapping("/info/{userId}")
    public R info(@PathVariable("userId") Long userId) {
        return R.ok().put("user", sysUserService.infoWithRoles(userId));
    }

    @PostMapping("/save")
    public R save(@RequestBody SysUserEntity user) {
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            return R.error("用户名不能为空");
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            return R.error("密码不能为空");
        }
        if (sysUserService.findByUsername(user.getUsername()) != null) {
            return R.error("用户名已存在");
        }
        sysUserService.save(user, CurrentUser.userId());
        return R.ok();
    }

    @PostMapping("/update")
    public R update(@RequestBody SysUserEntity user) {
        if (user.getUserId() == null) {
            return R.error("用户ID不能为空");
        }
        sysUserService.update(user);
        return R.ok();
    }

    /**
     * 批量删除。
     * <p>
     * 请求体是【裸 JSON 数组】：前端用 adornData(ids, false) 发送，不套对象。
     */
    @PostMapping("/delete")
    public R delete(@RequestBody List<Long> userIds) {
        try {
            sysUserService.delete(userIds, CurrentUser.userId());
        } catch (IllegalArgumentException ex) {
            // 业务性拒绝（删超管、删自己）要作为 code=500 加 msg 返回，
            // 让前端把原因弹给用户，而不是抛出去变成一个 500 错误页。
            return R.error(ex.getMessage());
        }
        return R.ok();
    }

    /** 修改当前登录用户的密码。 */
    @PostMapping("/password")
    public R password(@RequestBody Map<String, String> form) {
        String oldPassword = form.get("password");
        String newPassword = form.get("newPassword");
        if (newPassword == null || newPassword.isBlank()) {
            return R.error("新密码不能为空");
        }
        if (!sysUserService.updatePassword(CurrentUser.userId(), oldPassword, newPassword)) {
            return R.error("原密码不正确");
        }
        return R.ok();
    }
}
