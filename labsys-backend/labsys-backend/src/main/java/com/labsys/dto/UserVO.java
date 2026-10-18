package com.labsys.dto;

import com.labsys.entity.User;
import java.time.LocalDateTime;

public class UserVO {

    private Long id;
    private String username;
    private String role;
    private LocalDateTime createdAt;

    public UserVO(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.role = user.getRole();
        this.createdAt = user.getCreatedAt();
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
