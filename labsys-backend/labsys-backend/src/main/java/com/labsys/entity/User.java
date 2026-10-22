package com.labsys.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    /** 1 = 启用，0 = 停用。停用后在 AuthInterceptor 里直接拒掉，不用等令牌过期 */
    private Integer enabled;
    private LocalDateTime createdAt;

    public Long getId(){return id;}
    public String getUsername(){return username;}
    public String getPassword(){return password;}
    public Integer getEnabled(){return enabled;}
    public LocalDateTime getCreatedAt(){return createdAt;}

    public void setId(Long id) {
        this.id = id;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
