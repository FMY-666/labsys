package com.labsys.controller;

import com.labsys.common.Result;
import com.labsys.dto.UserCreateRequest;
import com.labsys.dto.UserEditRequest;
import com.labsys.service.RbacAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理，整组接口只给 ADMIN（拦截规则见 WebConfig）。
 * 注意和 UserController 里的 /api/users/options 是两个东西：
 * 那个是给下拉框用的轻量接口、实验员也要用，所以拦截规则写的是精确路径，不能是 /api/users/**。
 */
@RestController
@RequestMapping("/api/users")
public class UserAdminController {

    private final RbacAdminService rbacAdminService;

    public UserAdminController(RbacAdminService rbacAdminService) {
        this.rbacAdminService = rbacAdminService;
    }

    @GetMapping
    public Result list(@RequestParam(defaultValue = "1") int pageNum,
                       @RequestParam(defaultValue = "10") int pageSize,
                       @RequestParam(required = false) String keyword) {
        return rbacAdminService.listUsers(pageNum, pageSize, keyword);
    }

    @PostMapping
    public Result create(@RequestBody UserCreateRequest req) {
        return rbacAdminService.createUser(req.getUsername(), req.getPassword(), req.getRoleIds());
    }

    @PutMapping("/{id}/password")
    public Result resetPassword(@PathVariable Long id, @RequestBody UserEditRequest req) {
        return rbacAdminService.resetPassword(id, req.getPassword());
    }

    @PutMapping("/{id}/roles")
    public Result setRoles(@PathVariable Long id, @RequestBody UserEditRequest req) {
        return rbacAdminService.setRoles(id, req.getRoleIds());
    }

    @PutMapping("/{id}/enabled")
    public Result setEnabled(@PathVariable Long id,
                             @RequestParam Integer enabled,
                             @RequestAttribute("userId") Long operatorId) {
        return rbacAdminService.setEnabled(id, enabled, operatorId);
    }
}
