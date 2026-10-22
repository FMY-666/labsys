package com.labsys.controller;

import com.labsys.common.Result;
import com.labsys.dto.RoleMenuRequest;
import com.labsys.service.RbacAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 角色与菜单授权，只给 ADMIN */
@RestController
@RequestMapping("/api/roles")
public class RoleAdminController {

    private final RbacAdminService rbacAdminService;

    public RoleAdminController(RbacAdminService rbacAdminService) {
        this.rbacAdminService = rbacAdminService;
    }

    @GetMapping
    public Result list() {
        return rbacAdminService.listRoles();
    }

    /** 整组替换某个角色的菜单授权 */
    @PutMapping("/{id}/menus")
    public Result saveMenus(@PathVariable Long id, @RequestBody RoleMenuRequest req) {
        return rbacAdminService.saveRoleMenus(id, req.getMenuIds());
    }
}
