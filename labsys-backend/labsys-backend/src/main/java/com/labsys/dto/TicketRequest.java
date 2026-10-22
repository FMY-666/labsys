package com.labsys.dto;

/**
 * 提交报修的入参。reporterId 从 token 取，status 由服务端决定，都不允许前端传。
 */
public class TicketRequest {

    private Long deviceId;
    private String faultDesc;
    private String priority;

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public String getFaultDesc() {
        return faultDesc;
    }

    public void setFaultDesc(String faultDesc) {
        this.faultDesc = faultDesc;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }
}
