package com.labsys.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用户管理列表的一行。
 * 不带 password —— 这一页是给管理员看的，但没有任何理由把哈希发到浏览器。
 */
public class AdminUserVO {

    private Long id;
    private String username;
    private Integer enabled;
    private LocalDateTime createdAt;
    /** [{roleId, roleCode, roleName}]，一个用户可能有多个角色 */
    private List<Map<String, Object>> roles;
    /** 预约信用：分数 / 违规次数 / 是否被禁约，没有违规记录的人也返回全零的默认值 */
    private Map<String, Object> credit;

    public AdminUserVO(Long id, String username, Integer enabled, LocalDateTime createdAt,
                       List<Map<String, Object>> roles, Map<String, Object> credit) {
        this.id = id;
        this.username = username;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.roles = roles;
        this.credit = credit;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public Integer getEnabled() { return enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<Map<String, Object>> getRoles() { return roles; }
    public Map<String, Object> getCredit() { return credit; }
}
