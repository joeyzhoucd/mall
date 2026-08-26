package com.mall.admin.controller;

import com.mall.admin.entity.SysRoleEntity;
import com.mall.admin.service.SysRoleService;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理。
 * <p>
 * 授权相关的两个接口（info 与 save/update）涉及 -666666 哨兵，
 * 处理规则见 {@link SysRoleService} 的类注释和 openapi/admin-api.yaml 第六条。
 */
@RestController
@RequestMapping("/sys/role")
public class SysRoleController {

    private final SysRoleService sysRoleService;

    public SysRoleController(SysRoleService sysRoleService) {
        this.sysRoleService = sysRoleService;
    }

    @GetMapping("/list")
    public R list(@RequestParam(value = "page", defaultValue = "1") int page,
                  @RequestParam(value = "limit", defaultValue = "10") int limit,
                  @RequestParam(value = "roleName", required = false) String roleName) {
        return R.ok().put("page", new PageUtils(sysRoleService.page(page, limit, roleName)));
    }

    /**
     * 角色下拉列表（不分页）。
     * <p>
     * 注意 list 在响应【顶层】，不在 page 里面 —— 和分页接口的形状不同。
     * 前端 user-add-or-update.vue 直接读 data.list。
     */
    @GetMapping("/select")
    public R select() {
        return R.ok().put("list", sysRoleService.listAll());
    }

    @GetMapping("/info/{roleId}")
    public R info(@PathVariable("roleId") Long roleId) {
        return R.ok().put("role", sysRoleService.info(roleId));
    }

    @PostMapping("/save")
    public R save(@RequestBody SysRoleEntity role) {
        if (role.getRoleName() == null || role.getRoleName().isBlank()) {
            return R.error("角色名称不能为空");
        }
        sysRoleService.save(role, CurrentUser.userId());
        return R.ok();
    }

    @PostMapping("/update")
    public R update(@RequestBody SysRoleEntity role) {
        if (role.getRoleId() == null) {
            return R.error("角色ID不能为空");
        }
        sysRoleService.update(role);
        return R.ok();
    }

    @PostMapping("/delete")
    public R delete(@RequestBody List<Long> roleIds) {
        sysRoleService.delete(roleIds);
        return R.ok();
    }
}
