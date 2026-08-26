package com.mall.admin.controller;

import com.mall.admin.entity.SysMenuEntity;
import com.mall.admin.service.SysMenuService;
import com.mall.common.utils.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单管理。
 */
@RestController
@RequestMapping("/sys/menu")
public class SysMenuController {

    private final SysMenuService sysMenuService;

    public SysMenuController(SysMenuService sysMenuService) {
        this.sysMenuService = sysMenuService;
    }

    /**
     * 当前用户的导航菜单与权限串。
     * <p>
     * 这个响应【就是前端的路由表】：router/index.js 拿到 menuList 之后直接
     * fnAddDynamicMenuRoutes(data.menuList) 生成路由。所以这里返回错了不是"菜单显示不对"，
     * 而是整个后台没有可进入的页面。
     */
    @GetMapping("/nav")
    public R nav() {
        Long userId = CurrentUser.userId();
        return R.ok()
                .put("menuList", sysMenuService.navTree(userId))
                .put("permissions", sysMenuService.permissions(userId));
    }

    /**
     * 全部菜单。
     *
     * <h3>必须返回裸 JSON 数组，不能套 R 信封</h3>
     * 前端是这么用的（menu.vue）：
     * <pre>
     * this.dataList = treeDataTranslate(data, 'menuId')
     * </pre>
     * data 被直接当数组用，连 code 字段都不检查。一旦套上 {code,msg,...} 信封，
     * treeDataTranslate 拿到的是个对象，菜单管理页和角色授权树会【双双变成空的】，
     * 而且前后端都不报任何错。
     * <p>
     * 这是整个 mall-admin 里唯一一个不带信封的响应。如果以后有人给这个模块加统一的
     * 返回值包装器（ResponseBodyAdvice 之类），必须把这个接口排除掉，否则就会踩中上面那个坑。
     * 契约见 openapi/admin-api.yaml 第 5 条。
     */
    @GetMapping("/list")
    public List<SysMenuEntity> list() {
        return sysMenuService.listAll();
    }

    /** 可作为上级的菜单（含一个 menuId=0 的虚拟"一级菜单"根）。 */
    @GetMapping("/select")
    public R select() {
        return R.ok().put("menuList", sysMenuService.listForParentSelect());
    }

    @GetMapping("/info/{menuId}")
    public R info(@PathVariable("menuId") Long menuId) {
        return R.ok().put("menu", sysMenuService.info(menuId));
    }

    @PostMapping("/save")
    public R save(@RequestBody SysMenuEntity menu) {
        String error = sysMenuService.validate(menu);
        if (error != null) {
            return R.error(error);
        }
        sysMenuService.save(menu);
        return R.ok();
    }

    @PostMapping("/update")
    public R update(@RequestBody SysMenuEntity menu) {
        String error = sysMenuService.validate(menu);
        if (error != null) {
            return R.error(error);
        }
        sysMenuService.update(menu);
        return R.ok();
    }

    /**
     * 删除菜单。
     * <p>
     * 注意是【POST + 路径参数】。用户和角色的删除是 POST + 请求体数组，这里不一样——
     * 前端就是这么调的（menu.vue: adornUrl('/sys/menu/delete/' + id)），不能"统一"成数组形式。
     */
    @PostMapping("/delete/{menuId}")
    public R delete(@PathVariable("menuId") Long menuId) {
        if (sysMenuService.hasChildren(menuId)) {
            return R.error("请先删除子菜单");
        }
        sysMenuService.delete(menuId);
        return R.ok();
    }
}
