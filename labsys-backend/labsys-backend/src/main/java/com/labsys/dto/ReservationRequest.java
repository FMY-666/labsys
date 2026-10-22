package com.labsys.dto;

import java.time.LocalDateTime;

/**
 * 提交预约的入参。故意不包含 userId 和 status：
 * 前者只能从 token 里取，后者由服务端决定，都不允许前端指定。
 */
public class ReservationRequest {

    private Long deviceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String purpose;

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }
}
