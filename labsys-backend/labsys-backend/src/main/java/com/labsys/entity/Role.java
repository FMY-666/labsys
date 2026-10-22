package com.labsys.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_role")
public class Role {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String roleCode;
    private String roleName;
    private Integer enabled;

    public Long getId() { return id; }
    public String getRoleCode() { return roleCode; }
    public String getRoleName() { return roleName; }
    public Integer getEnabled() { return enabled; }

    public void setId(Long id) { this.id = id; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
}
