package com.labsys.dto;

/** 用户换自己的头像：传 base64 data URL，传空串表示恢复默认（首字母） */
public class AvatarRequest {

    private String avatar;

    public String getAvatar() { return avatar; }

    public void setAvatar(String avatar) { this.avatar = avatar; }
}
