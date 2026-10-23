package com.labsys.dto;

import com.labsys.entity.User;

import java.time.LocalDateTime;
import java.util.List;

public class UserVO {

    private Long id;
    private String username;
    private List<String> roles;
    private LocalDateTime createdAt;
    private String avatar;

    public UserVO(User user, List<String> roles) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.roles = roles;
        this.createdAt = user.getCreatedAt();
        this.avatar = user.getAvatar();
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public List<String> getRoles() { return roles; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getAvatar() { return avatar; }
}
