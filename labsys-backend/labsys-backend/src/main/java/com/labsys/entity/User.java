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
    /**
     * 头像，存的是 base64 data URL，为空表示没设过。
     * 之所以存库不存文件：本项目统一用 Bearer 令牌鉴权，而图片是用 img 标签加载的、带不了请求头，
     * 存文件就得再开一个匿名可读的静态路径出来。前端已经压到 160×160，单张两三万字节，mediumtext 够用。
     */
    private String avatar;

    public Long getId(){return id;}
    public String getUsername(){return username;}
    public String getPassword(){return password;}
    public Integer getEnabled(){return enabled;}
    public LocalDateTime getCreatedAt(){return createdAt;}
    public String getAvatar(){return avatar;}

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

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }
}
