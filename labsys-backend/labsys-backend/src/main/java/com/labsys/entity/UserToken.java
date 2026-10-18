package com.labsys.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_token")
public class UserToken {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String token;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    public Long getId() {return id;}

    public Long getUserId() {return userId;}
    public String getToken() {return token;}
    public LocalDateTime getCreatedAt() {return createdAt;}
    public LocalDateTime getExpiresAt() {return expiresAt;}

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}