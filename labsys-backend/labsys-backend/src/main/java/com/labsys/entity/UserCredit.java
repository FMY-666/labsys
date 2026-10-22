package com.labsys.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 预约信用。一人一行，主键就是 userId，没有违规记录的人不建行。
 * 分数不落库，由 violations 推导（见 CreditService），避免两份状态对不上。
 */
@TableName("sys_user_credit")
public class UserCredit {

    @TableId(type = IdType.INPUT)
    private Long userId;
    /** 当前未抵消的违规次数；按时归还 3 次抵掉 1 次 */
    private Integer violations;
    /** 距上次抵消已累计的按时归还次数，到 3 就抵一次违规并清零 */
    private Integer onTimeStreak;
    /** 禁止预约的截止时间，NULL 表示没禁 */
    private LocalDateTime bannedUntil;
    /** 1 = 封禁，必须管理员解禁 */
    private Integer permanentBan;
    private LocalDateTime updatedAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getViolations() {
        return violations;
    }

    public void setViolations(Integer violations) {
        this.violations = violations;
    }

    public Integer getOnTimeStreak() {
        return onTimeStreak;
    }

    public void setOnTimeStreak(Integer onTimeStreak) {
        this.onTimeStreak = onTimeStreak;
    }

    public LocalDateTime getBannedUntil() {
        return bannedUntil;
    }

    public void setBannedUntil(LocalDateTime bannedUntil) {
        this.bannedUntil = bannedUntil;
    }

    public Integer getPermanentBan() {
        return permanentBan;
    }

    public void setPermanentBan(Integer permanentBan) {
        this.permanentBan = permanentBan;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
