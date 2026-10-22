package com.labsys.dto;

import java.util.List;

/** 保存角色授权：整组菜单 id */
public class RoleMenuRequest {

    private List<Long> menuIds;

    public List<Long> getMenuIds() { return menuIds; }
    public void setMenuIds(List<Long> menuIds) { this.menuIds = menuIds; }
}
