package com.labsys.dto;

import com.labsys.entity.User;

public class LoginVO {
    private Long id;
    private String username;
    private String token;
    private String role;

    public LoginVO(User user, String token) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.token = token;
        this.role = user.getRole();
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

    public String getRole() {
        return role;
    }
}
