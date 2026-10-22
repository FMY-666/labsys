package com.labsys.dto;

/** 用户改自己的密码：要验原密码 */
public class PasswordRequest {

    private String oldPassword;
    private String newPassword;

    public String getOldPassword() { return oldPassword; }
    public String getNewPassword() { return newPassword; }

    public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
