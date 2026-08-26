package com.mall.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mall.admin.dao.SysMenuDao;
import com.mall.admin.dao.SysRoleMenuDao;
import com.mall.admin.dao.SysUserRoleDao;
import com.mall.admin.entity.SysMenuEntity;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 菜单与权限。
 *
 * <h3>这个服务的输出就是前端的路由表</h3>
 * {@code /sys/menu/nav} 的响应被 mall-frontend 的 router/index.js 直接拿去
 * fnAddDynamicMenuRoutes(menuList) 生成路由。也就是说菜单数据出错的表现不是
 * "菜单显示不对"，而是"整个后台没有可进入的页面"。
 */
@Service
public class SysMenuService {

    /** 超级管理员。它绕过所有权限过滤，看到全部菜单和权限。 */
    public static final long SUPER_ADMIN_ID = 1L;

    /** 菜单类型：目录 */
    private static final int TYPE_CATALOG = 0;
    /** 菜单类型：菜单 */
    private static final int TYPE_MENU = 1;
    /** 菜单类型：按钮。按钮不进导航树，只贡献 perms 字符串。 */
    private static final int TYPE_BUTTON = 2;

    private final SysMenuDao sysMenuDao;
    private final SysUserRoleDao sysUserRoleDao;
    private final SysRoleMenuDao sysRoleMenuDao;

    public SysMenuService(SysMenuDao sysMenuDao, SysUserRoleDao sysUserRoleDao, SysRoleMenuDao sysRoleMenuDao) {
        this.sysMenuDao = sysMenuDao;
        this.sysUserRoleDao = sysUserRoleDao;
        this.sysRoleMenuDao = sysRoleMenuDao;
    }

    /** 全部菜单，按上级和排序号排列，并填上 parentName。列表页用。 */
    public List<SysMenuEntity> listAll() {
        List<SysMenuEntity> all = sysMenuDao.selectList(
                new QueryWrapper<SysMenuEntity>().orderByAsc("parent_id", "order_num"));
        Map<Long, String> nameById = new HashMap<>();
        for (SysMenuEntity m : all) {
            nameById.put(m.getMenuId(), m.getName());
        }
        for (SysMenuEntity m : all) {
            // 一级菜单的 parent_id 是 0，表里没有 menu_id=0 的行，所以要单独给个名字，
            // 否则列表页的"上级菜单"一列会是空白。
            m.setParentName(m.getParentId() == null || m.getParentId() == 0L
                    ? "一级菜单"
                    : nameById.getOrDefault(m.getParentId(), ""));
        }
        return all;
    }

    /**
     * 可作为上级的菜单：只含目录和菜单，并在最前面插入一个 menuId=0 的虚拟根。
     * 那个虚拟根是前端下拉框需要的"一级菜单"选项，数据库里没有对应行。
     */
    public List<SysMenuEntity> listForParentSelect() {
        List<SysMenuEntity> list = sysMenuDao.selectList(
                new QueryWrapper<SysMenuEntity>()
                        .in("type", TYPE_CATALOG, TYPE_MENU)
                        .orderByAsc("parent_id", "order_num"));
        SysMenuEntity root = new SysMenuEntity();
        root.setMenuId(0L);
        root.setName("一级菜单");
        root.setParentId(-1L);
        root.setOrderNum(0);
        List<SysMenuEntity> result = new ArrayList<>(list.size() + 1);
        result.add(root);
        result.addAll(list);
        return result;
    }

    /**
     * 某个用户可见的导航树。
     *
     * @return 只含目录和菜单的树；每个节点的子节点放在名为 {@code list} 的字段里
     *         （字段名是契约，前端按这个名字递归）
     */
    public List<SysMenuEntity> navTree(Long userId) {
        List<SysMenuEntity> visible = visibleMenus(userId, TYPE_CATALOG, TYPE_MENU);
        return buildTree(visible, 0L);
    }

    /** 某个用户的权限标识集合，前端的 isAuth() 用它控制按钮显隐。 */
    public Set<String> permissions(Long userId) {
        Set<String> perms = new LinkedHashSet<>();
        for (SysMenuEntity m : visibleMenus(userId, TYPE_CATALOG, TYPE_MENU, TYPE_BUTTON)) {
            if (m.getPerms() == null || m.getPerms().isBlank()) {
                continue;
            }
            // perms 一列里可以写多个标识、逗号分隔，例如 "sys:user:list,sys:user:info"
            for (String p : m.getPerms().split(",")) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty()) {
                    perms.add(trimmed);
                }
            }
        }
        return perms;
    }

    /**
     * 取用户可见的菜单。
     * <p>
     * 超级管理员走"全量"分支，普通用户按 用户→角色→菜单 两跳查。
     * 普通用户没有任何角色时返回空列表，此时前端会得到一个空的路由表 ——
     * 登录能成功但没有任何可点的菜单。这是符合预期的（没授权就是没权限），
     * 只是排查时容易误判成"接口坏了"，所以在这里写明。
     */
    private List<SysMenuEntity> visibleMenus(Long userId, int... types) {
        QueryWrapper<SysMenuEntity> wrapper = new QueryWrapper<SysMenuEntity>()
                .in("type", Arrays.stream(types).boxed().toList())
                .orderByAsc("parent_id", "order_num");
        if (userId != null && userId == SUPER_ADMIN_ID) {
            return sysMenuDao.selectList(wrapper);
        }
        List<Long> roleIds = sysUserRoleDao.selectRoleIdsByUserId(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        Set<Long> menuIds = new HashSet<>();
        for (Long roleId : roleIds) {
            List<Long> ids = sysRoleMenuDao.selectMenuIdsByRoleId(roleId);
            if (ids != null) {
                menuIds.addAll(ids);
            }
        }
        // 过滤掉 -666666 哨兵：它只是前端用来分隔"全勾选/半勾选"的标记，不是真实菜单 id。
        // 这里【必须】过滤（而角色详情接口里【必须不】过滤），两处要求正好相反，
        // 原因见 openapi/admin-api.yaml 第六条。
        menuIds.removeIf(id -> id == null || id < 0);
        if (menuIds.isEmpty()) {
            return List.of();
        }
        wrapper.in("menu_id", menuIds);
        return sysMenuDao.selectList(wrapper);
    }

    /**
     * 把扁平列表拼成树。
     * <p>
     * 只保留能从根连上的节点：如果一个菜单被授权、但它的父目录没被授权，
     * 那它在树里就没有落脚点，会被丢掉。这是刻意的 —— 前端的路由是按树生成的，
     * 挂不上父节点的菜单本来也进不了导航。
     */
    private List<SysMenuEntity> buildTree(List<SysMenuEntity> all, Long parentId) {
        List<SysMenuEntity> children = new ArrayList<>();
        for (SysMenuEntity m : all) {
            Long pid = m.getParentId() == null ? 0L : m.getParentId();
            if (pid.equals(parentId)) {
                m.setList(buildTree(all, m.getMenuId()));
                children.add(m);
            }
        }
        return children;
    }
public SysMenuEntity info(Long menuId) {
        return sysMenuDao.selectById(menuId);
    }

    /**
     * 保存前的校验。
     *
     * @return 出错时返回给用户看的提示；通过则返回 null
     */
    public String validate(SysMenuEntity menu) {
        if (menu == null || menu.getName() == null || menu.getName().isBlank()) {
            return "菜单名称不能为空";
        }
        if (menu.getType() == null) {
            return "菜单类型不能为空";
        }
        // 目录和菜单必须有 url，否则前端生成的路由指向空路径，点进去是空白页。
        // 按钮（type=2）不需要 url，它只贡献 perms。
        if (menu.getType() != TYPE_BUTTON && (menu.getUrl() == null || menu.getUrl().isBlank())) {
            return "菜单URL不能为空";
        }
        // 目录的上级只能是目录（或一级）。菜单挂在菜单下面前端渲染不出来。
        if (menu.getParentId() != null && menu.getParentId() > 0) {
            SysMenuEntity parent = sysMenuDao.selectById(menu.getParentId());
            if (parent == null) {
                return "上级菜单不存在";
            }
            if (menu.getType() == TYPE_MENU && parent.getType() != null && parent.getType() != TYPE_CATALOG) {
                return "菜单的上级只能是目录";
            }
        }
        return null;
    }

    public void save(SysMenuEntity menu) {
        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
        sysMenuDao.insert(menu);
    }

    public void update(SysMenuEntity menu) {
        sysMenuDao.updateById(menu);
    }

    /** 是否还有子菜单。有子菜单时不允许删除，否则子菜单会变成挂不上树的孤儿数据。 */
    public boolean hasChildren(Long menuId) {
        return sysMenuDao.selectCount(new QueryWrapper<SysMenuEntity>().eq("parent_id", menuId)) > 0;
    }

    public void delete(Long menuId) {
        sysMenuDao.deleteById(menuId);
    }
}
