package com.pcmvp.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pcmvp.policy.domain.PolicyLibrary;
import com.pcmvp.policy.mapper.PolicyLibraryMapper;
import com.pcmvp.system.SystemCatalog;
import com.pcmvp.system.domain.SysRole;
import com.pcmvp.system.domain.SysRoleLibrary;
import com.pcmvp.system.domain.SysRoleMenu;
import com.pcmvp.system.domain.SysUser;
import com.pcmvp.system.mapper.SysRoleLibraryMapper;
import com.pcmvp.system.mapper.SysRoleMapper;
import com.pcmvp.system.mapper.SysRoleMenuMapper;
import com.pcmvp.system.mapper.SysUserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper menuMapper;
    private final SysRoleLibraryMapper libraryGrantMapper;
    private final PolicyLibraryMapper libraryMapper;

    public SystemService(SysUserMapper userMapper, SysRoleMapper roleMapper, SysRoleMenuMapper menuMapper,
                         SysRoleLibraryMapper libraryGrantMapper, PolicyLibraryMapper libraryMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.libraryGrantMapper = libraryGrantMapper;
        this.libraryMapper = libraryMapper;
    }

    public Map<String, Object> bootstrap(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, "lin").last("LIMIT 1"));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user", view(user));
        out.put("menus", menuKeys(user.getRoleId()));
        out.put("libraryIds", libraryIds(user.getRoleId()));
        out.put("users", users());
        out.put("catalog", SystemCatalog.GROUPS);
        return out;
    }

    public List<Map<String, Object>> users() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>().orderByAsc(SysUser::getId))
                .stream().map(this::view).toList();
    }

    public List<SysRole> roles() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId));
    }

    public Map<String, Object> roleGrants(Long roleId) {
        requireRole(roleId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("menus", menuKeys(roleId));
        out.put("libraryIds", libraryIds(roleId));
        out.put("libraries", libraryMapper.selectList(new LambdaQueryWrapper<PolicyLibrary>().orderByAsc(PolicyLibrary::getId)));
        return out;
    }

    @Transactional
    public SysUser createUser(String username, String displayName, Long roleId) {
        if (username == null || username.isBlank() || displayName == null || displayName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "账号和姓名都要填");
        }
        requireRole(roleId);
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username.trim()));
        if (exists > 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "账号已存在");
        SysUser user = new SysUser();
        user.setUsername(username.trim());
        user.setDisplayName(displayName.trim());
        user.setRoleId(roleId);
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    @Transactional
    public SysUser updateUser(Long id, String displayName, Long roleId, Boolean enabled) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        if (roleId != null) {
            requireRole(roleId);
            user.setRoleId(roleId);
        }
        if (displayName != null && !displayName.isBlank()) user.setDisplayName(displayName.trim());
        if (enabled != null) user.setEnabled(enabled);
        if (!Boolean.TRUE.equals(user.getEnabled()) || !isAdmin(user.getRoleId())) {
            ensureAdminRemains(user.getId());
        }
        userMapper.updateById(user);
        return user;
    }

    @Transactional
    public void saveGrants(Long roleId, List<String> menus, List<Long> libraryIds) {
        SysRole role = requireRole(roleId);
        List<String> keys = menus == null ? List.of() : menus.stream().filter(SystemCatalog.KEYS::contains).distinct().toList();
        if ("admin".equals(role.getCode())) {
            if (!keys.contains("/system/users")) keys = append(keys, "/system/users");
            if (!keys.contains("/system/roles")) keys = append(keys, "/system/roles");
        }
        menuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        for (String key : keys) {
            SysRoleMenu row = new SysRoleMenu();
            row.setRoleId(roleId);
            row.setMenuKey(key);
            menuMapper.insert(row);
        }
        libraryGrantMapper.delete(new LambdaQueryWrapper<SysRoleLibrary>().eq(SysRoleLibrary::getRoleId, roleId));
        if (libraryIds != null) {
            for (Long libraryId : libraryIds.stream().distinct().toList()) {
                if (libraryMapper.selectById(libraryId) == null) continue;
                SysRoleLibrary row = new SysRoleLibrary();
                row.setRoleId(roleId);
                row.setLibraryId(libraryId);
                libraryGrantMapper.insert(row);
            }
        }
    }

    private Map<String, Object> view(SysUser user) {
        SysRole role = roleMapper.selectById(user.getRoleId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("username", user.getUsername());
        row.put("displayName", user.getDisplayName());
        row.put("roleId", user.getRoleId());
        row.put("roleName", role == null ? "" : role.getName());
        row.put("enabled", Boolean.TRUE.equals(user.getEnabled()));
        return row;
    }

    private List<String> menuKeys(Long roleId) {
        return menuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId))
                .stream().map(SysRoleMenu::getMenuKey).toList();
    }

    private List<Long> libraryIds(Long roleId) {
        return libraryGrantMapper.selectList(new LambdaQueryWrapper<SysRoleLibrary>().eq(SysRoleLibrary::getRoleId, roleId))
                .stream().map(SysRoleLibrary::getLibraryId).toList();
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色不存在");
        return role;
    }

    private boolean isAdmin(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        return role != null && "admin".equals(role.getCode());
    }

    private void ensureAdminRemains(Long excludingUserId) {
        SysRole admin = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, "admin").last("LIMIT 1"));
        if (admin == null) return;
        Long left = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRoleId, admin.getId())
                .eq(SysUser::getEnabled, true)
                .ne(SysUser::getId, excludingUserId));
        if (left == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "至少保留一名启用的系统管理员");
    }

    private static List<String> append(List<String> keys, String key) {
        if (keys.contains(key)) return keys;
        java.util.ArrayList<String> next = new java.util.ArrayList<>(keys);
        next.add(key);
        return next;
    }
}
