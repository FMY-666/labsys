package com.labsys.dto;

import java.util.List;

/** 新建用户：用户名 + 初始密码 + 角色 */
public class UserCreateRequest {

    private String username;
    private String password;
    private List<Long> roleIds;

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public List<Long> getRoleIds() { return roleIds; }

    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds; }
}
