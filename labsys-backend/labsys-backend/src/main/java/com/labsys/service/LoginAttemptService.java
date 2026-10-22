package com.labsys.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败次数限制。
 *
 * 同一用户名连续失败 5 次，锁 10 分钟。按用户名而不是按 IP 计数：
 * 这个系统是在校园网里用的，一台机器后面可能坐着整个班，按 IP 会连坐。
 *
 * 计数放在内存里，重启即失效 —— 单实例部署够用。
 * 要多实例或者重启后仍然生效，得换成 Redis 或一张失败记录表，这里不做，
 * 因为这个功能的目标是挡住脚本暴力试密码，不是审计。
 */
@Service
public class LoginAttemptService {

    private static final int MAX_FAILS = 5;
    private static final int LOCK_MINUTES = 10;

    private static final class Attempt {
        int fails;
        LocalDateTime lockedUntil;
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** 还剩多少分钟解锁；没被锁返回 0 */
    public long lockedMinutes(String username) {
        Attempt a = attempts.get(key(username));
        if (a == null || a.lockedUntil == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        if (a.lockedUntil.isAfter(now)) {
            return Math.max(1, java.time.Duration.between(now, a.lockedUntil).toMinutes() + 1);
        }
        // 锁已过期，顺手清掉，免得表越积越大
        attempts.remove(key(username));
        return 0;
    }

    public void onFailure(String username) {
        Attempt a = attempts.computeIfAbsent(key(username), k -> new Attempt());
        a.fails++;
        if (a.fails >= MAX_FAILS) {
            a.lockedUntil = LocalDateTime.now().plusMinutes(LOCK_MINUTES);
            a.fails = 0;
        }
    }

    public void onSuccess(String username) {
        attempts.remove(key(username));
    }

    /** 用户名统一小写，避免 Alice / alice 各算一份 */
    private String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
