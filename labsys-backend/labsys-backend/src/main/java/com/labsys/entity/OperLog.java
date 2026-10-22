package com.labsys.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_oper_log")
public class OperLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String method;
    private String path;
    private String ip;
    private Integer success;
    private Integer costMs;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getMethod() { return method; }
    public String getPath() { return path; }
    public String getIp() { return ip; }
    public Integer getSuccess() { return success; }
    public Integer getCostMs() { return costMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setUsername(String username) { this.username = username; }
    public void setMethod(String method) { this.method = method; }
    public void setPath(String path) { this.path = path; }
    public void setIp(String ip) { this.ip = ip; }
    public void setSuccess(Integer success) { this.success = success; }
    public void setCostMs(Integer costMs) { this.costMs = costMs; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
