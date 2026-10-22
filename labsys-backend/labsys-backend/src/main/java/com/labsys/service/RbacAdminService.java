package com.labsys.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labsys.common.Result;
import com.labsys.dto.AdminUserVO;
import com.labsys.dto.PageVO;
import com.labsys.entity.Menu;
import com.labsys.entity.Role;
import com.labsys.entity.User;
import com.labsys.entity.UserCredit;
import com.labsys.mapper.GrantMapper;
import com.labsys.mapper.MenuMapper;
import com.labsys.mapper.RoleMapper;
import com.labsys.mapper.UserCreditMapper;
import com.labsys.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理与角色授权的后台逻辑。
 *
 * 这里所有的“防呆”都指向同一个风险：把系统改成没人管得了的状态。
 * 具体三条：
 *   ① 最后一个启用的管理员不能被停用
 *   ② 最后一个管理员不能摘掉 ADMIN 角色
 *   ③ ADMIN 角色必须保留「用户管理」和「角色管理」，否则下次谁也改不了授权
 * 这三条都不是安全边界（ADMIN 本来就有全部权限），而是防止管理员手滑把自己锁在门外。
 */
@Service
public class RbacAdminService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final MenuMapper menuMapper;
    private final GrantMapper grantMapper;
    private final UserCreditMapper creditMapper;
    private final CreditService creditService;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public RbacAdminService(UserMapper userMapper, RoleMapper roleMapper,
                            MenuMapper menuMapper, GrantMapper grantMapper,
                            UserCreditMapper creditMapper, CreditService creditService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.grantMapper = grantMapper;
        this.creditMapper = creditMapper;
        this.creditService = creditService;
    }

    // ---------------- 用户 ----------------

    public Result listUsers(int pageNum, int pageSize, String keyword) {
        Page<User> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(User::getUsername, keyword.trim());
        }
        wrapper.orderByAsc(User::getId);
        userMapper.selectPage(page, wrapper);

        List<Long> ids = page.getRecords().stream().map(User::getId).collect(Collectors.toList());

        Map<Long, List<Map<String, Object>>> rolesByUser = new HashMap<>();
        Map<Long, UserCredit> creditByUser = new HashMap<>();
        if (!ids.isEmpty()) {
            // 一次查完这一页所有人的角色和信用，避免按人循环查（N+1）
            for (Map<String, Object> row : roleMapper.findRolesByUserIds(ids)) {
                Long uid = ((Number) row.get("userId")).longValue();
                rolesByUser.computeIfAbsent(uid, k -> new ArrayList<>()).add(row);
            }
            // 主键就是 user_id，直接按主键批量查；没违规的人查不到，用全零默认值补齐
            for (UserCredit c : creditMapper.selectBatchIds(ids)) {
                creditByUser.put(c.getUserId(), c);
            }
        }

        List<AdminUserVO> list = page.getRecords().stream()
                .map(u -> new AdminUserVO(u.getId(), u.getUsername(), u.getEnabled(), u.getCreatedAt(),
                        rolesByUser.getOrDefault(u.getId(), new ArrayList<>()),
                        creditService.viewOrEmpty(creditByUser.get(u.getId()))))
                .collect(Collectors.toList());

        return new Result(200, "ok", new PageVO<>(page.getTotal(), list));
    }

    @Transactional
    public Result createUser(String username, String password, List<Long> roleIds) {
        if (username == null || username.trim().length() < 2) {
            return new Result(400, "用户名至少 2 个字符", null);
        }
        if (password == null || password.length() < 6) {
            return new Result(400, "密码至少 6 位", null);
        }
        if (roleIds == null || roleIds.isEmpty()) {
            return new Result(400, "至少要分配一个角色", null);
        }

        String name = username.trim();
        if (userMapper.findByUsername(name) != null) {
            return new Result(400, "用户名已存在", null);
        }

        RoleCheck check = checkRoles(roleIds);
        if (check.error != null) {
            return check.error;
        }

        User u = new User();
        u.setUsername(name);
        u.setPassword(encoder.encode(password));
        u.setEnabled(1);
        u.setCreatedAt(LocalDateTime.now());
        userMapper.insert(u);

        grantMapper.insertUserRoles(u.getId(), check.ids);
        return new Result(200, "ok", u.getId());
    }

    public Result resetPassword(Long userId, String password) {
        if (password == null || password.length() < 6) {
            return new Result(400, "密码至少 6 位", null);
        }
        User u = userMapper.selectById(userId);
        if (u == null) {
            return new Result(404, "用户不存在", null);
        }
        u.setPassword(encoder.encode(password));
        userMapper.updateById(u);
        return new Result(200, "密码已重置", null);
    }

    @Transactional
    public Result setRoles(Long userId, List<Long> roleIds) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            return new Result(404, "用户不存在", null);
        }
        if (roleIds == null || roleIds.isEmpty()) {
            return new Result(400, "至少要分配一个角色", null);
        }

        RoleCheck check = checkRoles(roleIds);
        if (check.error != null) {
            return check.error;
        }

        boolean wasAdmin = roleMapper.findRoleCodesByUserId(userId).contains("ADMIN");
        boolean willAdmin = check.containsAdmin();
        if (wasAdmin && !willAdmin && roleMapper.countEnabledAdmins() <= 1) {
            return new Result(400, "这是最后一个启用的管理员，不能去掉它的管理员角色", null);
        }

        grantMapper.deleteUserRoles(userId);
        grantMapper.insertUserRoles(userId, check.ids);
        return new Result(200, "ok", null);
    }

    public Result setEnabled(Long userId, Integer enabled, Long operatorId) {
        if (enabled == null || (enabled != 0 && enabled != 1)) {
            return new Result(400, "enabled 只能是 0 或 1", null);
        }
        User u = userMapper.selectById(userId);
        if (u == null) {
            return new Result(404, "用户不存在", null);
        }
        if (enabled == 0) {
            if (userId.equals(operatorId)) {
                return new Result(400, "不能停用当前登录的账号", null);
            }
            boolean isAdmin = roleMapper.findRoleCodesByUserId(userId).contains("ADMIN");
            if (isAdmin && roleMapper.countEnabledAdmins() <= 1) {
                return new Result(400, "这是最后一个启用的管理员，停用后没人能管理系统", null);
            }
        }
        u.setEnabled(enabled);
        userMapper.updateById(u);
        return new Result(200, "ok", null);
    }

    // ---------------- 角色与菜单授权 ----------------

    public Result listRoles() {
        List<Role> roles = roleMapper.selectList(new LambdaQueryWrapper<Role>().orderByAsc(Role::getId));

        Map<Long, List<Long>> byRole = new HashMap<>();
        for (Map<String, Object> row : roleMapper.findAllRoleMenus()) {
            Long roleId = ((Number) row.get("roleId")).longValue();
            Long menuId = ((Number) row.get("menuId")).longValue();
            byRole.computeIfAbsent(roleId, k -> new ArrayList<>()).add(menuId);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Role r : roles) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("roleCode", r.getRoleCode());
            m.put("roleName", r.getRoleName());
            m.put("enabled", r.getEnabled());
            m.put("menuIds", byRole.getOrDefault(r.getId(), new ArrayList<>()));
            out.add(m);
        }
        return new Result(200, "ok", out);
    }

    /** 全部菜单（扁平），前端自己拼成树做勾选框 */
    public Result allMenus() {
        return new Result(200, "ok", menuMapper.findAll());
    }

    /**
     * 保存某个角色的菜单授权。整组替换。
     * 父目录会被自动补齐：只勾了子页面没勾目录，子菜单在拼树时会找不到父节点被丢掉，
     * 这个坑在菜单模块踩过一次，所以这里不让前端负责补。
     */
    @Transactional
    public Result saveRoleMenus(Long roleId, List<Long> menuIds) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            return new Result(404, "角色不存在", null);
        }
        if (menuIds == null || menuIds.isEmpty()) {
            return new Result(400, "至少要保留一个菜单", null);
        }

        Map<Long, Menu> all = new HashMap<>();
        menuMapper.findAll().forEach(m -> all.put(m.getId(), m));

        Set<Long> ids = new LinkedHashSet<>();
        for (Long id : menuIds) {
            if (id == null || !all.containsKey(id)) {
                return new Result(400, "菜单不存在：" + id, null);
            }
            ids.add(id);
        }

        // 补齐父目录
        for (Long id : new ArrayList<>(ids)) {
            Long parent = all.get(id).getParentId();
            while (parent != null && parent != 0) {
                ids.add(parent);
                Menu p = all.get(parent);
                parent = (p == null) ? 0 : p.getParentId();
            }
        }

        // 防呆：管理员角色不能把用户管理/角色管理去掉
        if ("ADMIN".equals(role.getRoleCode())) {
            boolean keepUsers = hasPath(ids, all, "/users");
            boolean keepRoles = hasPath(ids, all, "/roles");
            if (!keepUsers || !keepRoles) {
                return new Result(400, "管理员角色必须保留「用户管理」和「角色管理」，否则没人能再修改授权", null);
            }
        }

        grantMapper.deleteRoleMenus(roleId);
        grantMapper.insertRoleMenus(roleId, new ArrayList<>(ids));
        return new Result(200, "ok", null);
    }

    private boolean hasPath(Set<Long> ids, Map<Long, Menu> all, String path) {
        for (Long id : ids) {
            Menu m = all.get(id);
            if (m != null && path.equals(m.getPath())) {
                return true;
            }
        }
        return false;
    }

    /** 校验 roleIds 都存在，并顺便去重（重复的会撞 uk_user_role） */
    private RoleCheck checkRoles(List<Long> roleIds) {
        Set<Long> ids = new LinkedHashSet<>(roleIds);
        List<Role> roles = roleMapper.selectBatchIds(ids);
        if (roles.size() != ids.size()) {
            return new RoleCheck(null, null, new Result(400, "角色不存在", null));
        }
        return new RoleCheck(new ArrayList<>(ids), roles, null);
    }

    private static class RoleCheck {
        final List<Long> ids;
        final List<Role> roles;
        final Result error;

        RoleCheck(List<Long> ids, List<Role> roles, Result error) {
            this.ids = ids;
            this.roles = roles;
            this.error = error;
        }

        boolean containsAdmin() {
            return roles != null && roles.stream().anyMatch(r -> "ADMIN".equals(r.getRoleCode()));
        }
    }
}
