package com.pcmvp.system.controller;

import com.pcmvp.system.domain.SysRole;
import com.pcmvp.system.domain.SysUser;
import com.pcmvp.system.service.SystemService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemService service;

    public SystemController(SystemService service) {
        this.service = service;
    }

    @GetMapping("/bootstrap")
    public Map<String, Object> bootstrap(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return service.bootstrap(userId);
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        return service.users();
    }

    @PostMapping("/users")
    public SysUser createUser(@RequestBody Map<String, Object> body) {
        return service.createUser(str(body.get("username")), str(body.get("displayName")), longVal(body.get("roleId")));
    }

    @PutMapping("/users/{id}")
    public SysUser updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Boolean enabled = body.get("enabled") == null ? null : Boolean.valueOf(body.get("enabled").toString());
        return service.updateUser(id, str(body.get("displayName")),
                body.get("roleId") == null ? null : longVal(body.get("roleId")), enabled);
    }

    @GetMapping("/roles")
    public List<SysRole> roles() {
        return service.roles();
    }

    @GetMapping("/roles/{id}")
    public Map<String, Object> role(@PathVariable Long id) {
        return service.roleGrants(id);
    }

    @PutMapping("/roles/{id}/grants")
    public void saveGrants(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> menus = body.get("menus") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of();
        @SuppressWarnings("unchecked")
        List<Long> libraries = body.get("libraryIds") instanceof List<?> list
                ? list.stream().map(v -> Long.valueOf(v.toString())).toList() : List.of();
        service.saveGrants(id, menus, libraries);
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static Long longVal(Object v) {
        return v == null ? null : Long.valueOf(v.toString());
    }
}
