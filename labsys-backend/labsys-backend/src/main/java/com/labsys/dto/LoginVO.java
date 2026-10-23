package com.labsys.dto;

import com.labsys.entity.User;

import java.util.List;

public class LoginVO {

    private Long id;
    private String username;
    private String token;
    private List<String> roles;
    private List<String> permissions;
    private String avatar;

    public LoginVO(User user, String token, List<String> roles, List<String> permissions) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.token = token;
        this.roles = roles;
        this.permissions = permissions;
        // 登录就把头像带回去，前端不用为了一个头像再多请求一次 /api/me
        this.avatar = user.getAvatar();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getToken() {
        return token;
    }

    public List<String> getRoles() {
        return roles;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public String getAvatar() {
        return avatar;
    }
}
