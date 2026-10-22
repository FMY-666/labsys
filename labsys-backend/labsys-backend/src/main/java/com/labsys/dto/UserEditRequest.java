package com.labsys.dto;

/** 改密码 / 改角色 两个接口共用，各自只读自己那个字段 */
public class UserEditRequest {

    private String password;
    private java.util.List<Long> roleIds;

    public String getPassword() { return password; }
    public java.util.List<Long> getRoleIds() { return roleIds; }

    public void setPassword(String password) { this.password = password; }
    public void setRoleIds(java.util.List<Long> roleIds) { this.roleIds = roleIds; }
}
