package com.labsys.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labsys.common.Result;
import com.labsys.entity.User;
import com.labsys.entity.UserCredit;
import com.labsys.entity.Reservation;
import com.labsys.mapper.ReservationMapper;
import com.labsys.mapper.UserCreditMapper;
import com.labsys.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 预约信用与违规处罚。
 *
 * 规则全在这一个类里，改口径只改这里：
 *   违规 1 次        -> 警告（不限制，只是提示）
 *   违规 2、3 次     -> 禁止预约 24 小时
 *   违规 4 次及以上  -> 封禁，必须管理员解禁
 *   按时归还累计 3 次 -> 抵掉 1 次违规
 *   违规 ≥ 2 次      -> 单次最长预约时长降为 1/4
 *
 * 分数不落库，由违规次数算出来：100 - 10 × violations。
 * 存两份迟早对不上（抵消一次违规时两处都要改，漏一处就出现“分数 80 但只警告过一次”）。
 */
@Service
public class CreditService {

    /** 没有违规时的单次最长预约时长，和前端 OPEN_TO - OPEN_FROM 对齐 */
    public static final int BASE_MAX_HOURS = 14;
    /** 违规达到这个次数起，单次时长降为 1/4 */
    private static final int LIMITED_FROM = 2;
    /** 违规达到这个次数直接封禁，不再自动解封 */
    private static final int BAN_AT = 4;
    /** 每次禁约多少天 */
    private static final int BAN_DAYS = 1;
    /** 按时归还多少次抵一次违规 */
    private static final int ON_TIME_TO_OFFSET = 3;
    /** 每次违规扣多少分 */
    private static final int POINTS_PER_VIOLATION = 10;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final UserCreditMapper creditMapper;
    private final UserMapper userMapper;
    private final ReservationMapper reservationMapper;

    public CreditService(UserCreditMapper creditMapper, UserMapper userMapper,
                         ReservationMapper reservationMapper) {
        this.creditMapper = creditMapper;
        this.userMapper = userMapper;
        this.reservationMapper = reservationMapper;
    }

    /** 查不到就建一行全零的，调用方不用判空 */
    public UserCredit getOrCreate(Long userId) {
        UserCredit c = creditMapper.selectById(userId);
        if (c != null) {
            return c;
        }
        c = new UserCredit();
        c.setUserId(userId);
        c.setViolations(0);
        c.setOnTimeStreak(0);
        c.setPermanentBan(0);
        c.setUpdatedAt(LocalDateTime.now());
        creditMapper.insert(c);
        return c;
    }

    /**
     * 保存。这里不能只靠 updateById —— MyBatis-Plus 默认跳过值为 null 的字段，
     * 解禁时 setBannedUntil(null) 会被静默丢掉，数据库里的禁约时间还在，
     * 结果就是“违规清零了但学生还是不能约”。所以 null 的情况单独发一条 UPDATE 显式写 NULL。
     */
    private void save(UserCredit c) {
        c.setUpdatedAt(LocalDateTime.now());
        creditMapper.updateById(c);
        if (c.getBannedUntil() == null) {
            creditMapper.clearBanUntil(c.getUserId());
        }
    }

    /** 100 分制，只用于展示，扣到 0 就不再往下掉 */
    public int score(UserCredit c) {
        return Math.max(0, 100 - POINTS_PER_VIOLATION * violationsOf(c));
    }

    public int maxHours(UserCredit c) {
        return violationsOf(c) >= LIMITED_FROM ? BASE_MAX_HOURS / 4 : BASE_MAX_HOURS;
    }

    public boolean blocked(UserCredit c) {
        if (violationsOf(c) >= BAN_AT) {
            return true;
        }
        return c.getBannedUntil() != null && c.getBannedUntil().isAfter(LocalDateTime.now());
    }

    private int violationsOf(UserCredit c) {
        return c.getViolations() == null ? 0 : c.getViolations();
    }

    private int streakOf(UserCredit c) {
        return c.getOnTimeStreak() == null ? 0 : c.getOnTimeStreak();
    }

    /**
     * 记一次超时违规。禁约时间从违规时刻起算，不跟着后续操作滚动，
     * 否则同一个人连续违规会把禁约越推越远，规则变得没法解释。
     */
    public void applyOverdue(Long userId) {
        UserCredit c = getOrCreate(userId);
        int v = violationsOf(c) + 1;
        c.setViolations(v);

        if (v >= BAN_AT) {
            c.setPermanentBan(1);
            c.setBannedUntil(null);
        } else if (v >= LIMITED_FROM) {
            c.setBannedUntil(LocalDateTime.now().plusDays(BAN_DAYS));
        }
        save(c);
    }

    /**
     * 按时归还：攒够 3 次抵一次违规。
     * 违规已经是 0 时只在攒次数，不做负数削减 —— 提前“攒积分”会让处罚形同虚设。
     *
     * @return 本次是否抵掉了一次违规
     */
    public boolean applyOnTime(Long userId) {
        UserCredit c = getOrCreate(userId);
        int streak = streakOf(c) + 1;
        boolean offset = false;

        if (streak >= ON_TIME_TO_OFFSET) {
            streak = 0;
            if (violationsOf(c) > 0) {
                c.setViolations(violationsOf(c) - 1);
                offset = true;
            }
        }
        c.setOnTimeStreak(streak);
        save(c);
        return offset;
    }

    /** 管理员解禁：违规清零、封禁解除、攒的次数也归零，等于重新开始 */
    public void reset(Long userId) {
        UserCredit c = getOrCreate(userId);
        c.setViolations(0);
        c.setOnTimeStreak(0);
        c.setBannedUntil(null);
        c.setPermanentBan(0);
        save(c);
    }

    /**
     * 提交预约前的准入检查。返回 null 表示放行。
     * 时长限制只在这里拦一次：前端也算了同样的上限，但前端算的不算数。
     */
    public Result checkBooking(Long userId, LocalDateTime start, LocalDateTime end) {
        UserCredit c = getOrCreate(userId);

        if (blocked(c)) {
            return new Result(403, blockMessage(c), null);
        }
        int max = maxHours(c);
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes > max * 60L) {
            return new Result(400, "近期有超时记录，单次最多只能预约 " + max + " 小时", null);
        }
        return null;
    }

    private String blockMessage(UserCredit c) {
        if (violationsOf(c) >= BAN_AT) {
            return "预约权限已被封禁（累计违规 " + violationsOf(c) + " 次），请联系管理员解禁";
        }
        return "预约权限暂时冻结到 " + c.getBannedUntil().format(FMT) + "，解冻后才能再约";
    }

    /** 给学生自己看的实时状态 */
    public Map<String, Object> view(UserCredit c) {        Map<String, Object> m = new HashMap<>();
        int v = violationsOf(c);
        int streak = streakOf(c);

        m.put("score", score(c));
        m.put("violations", v);
        m.put("onTimeStreak", streak);
        m.put("offsetRemain", ON_TIME_TO_OFFSET - streak);
        m.put("maxHours", maxHours(c));
        m.put("permanentBan", v >= BAN_AT ? 1 : 0);
        m.put("bannedUntil", c.getBannedUntil());
        m.put("blocked", blocked(c));
        m.put("blockReason", blocked(c) ? blockMessage(c) : null);
        m.put("level", level(v));
        m.put("levelText", levelText(c, v));
        return m;
    }

    /**
     * 同上，但没有记录的人不要为了显示一行数据就去 insert 一行。
     * 用户管理页会把所有人的信用都列出来，用 getOrCreate 会给每个没违规的人凭空插一行。
     */
    public Map<String, Object> viewOrEmpty(UserCredit c) {
        if (c != null) {
            return view(c);
        }
        UserCredit empty = new UserCredit();
        empty.setViolations(0);
        empty.setOnTimeStreak(0);
        empty.setPermanentBan(0);
        return view(empty);
    }

    private String level(int v) {
        if (v <= 0) return "NORMAL";
        if (v == 1) return "WARN";
        if (v < BAN_AT) return "FROZEN";
        return "BANNED";
    }

    private String levelText(UserCredit c, int v) {
        if (v <= 0) return "信用正常";
        if (v == 1) return "警告 1 次";
        if (v < BAN_AT) {
            return blocked(c) ? "禁约中（到 " + c.getBannedUntil().format(FMT) + "）" : "违规 " + v + " 次";
        }
        return "已封禁，需管理员解禁";
    }

    /** 学生自己看：哪几次超时导致扣分。只有汇总数字的话，学生不服也没法申诉 */
    public List<Reservation> myViolations(Long userId) {
        return reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .eq(Reservation::getOverdueViolation, 1)
                .orderByDesc(Reservation::getEndTime)
                .last("LIMIT 20"));
    }

    /**
     * 管理员看板：只列出有违规或处于封禁状态的用户，没违规的人不必占屏幕。
     * 用户名单独查一次再拼，不加 SQL JOIN —— 表只有两张，避免多写一条手写 SQL。
     */
    public List<Map<String, Object>> listAbnormal() {
        List<UserCredit> rows = creditMapper.selectList(new LambdaQueryWrapper<UserCredit>()
                .gt(UserCredit::getViolations, 0)
                .or()
                .eq(UserCredit::getPermanentBan, 1)
                .orderByDesc(UserCredit::getViolations));

        if (rows.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> ids = rows.stream().map(UserCredit::getUserId).collect(Collectors.toList());
        Map<Long, String> names = userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        List<Map<String, Object>> out = new ArrayList<>();
        for (UserCredit c : rows) {
            Map<String, Object> m = view(c);
            m.put("userId", c.getUserId());
            m.put("username", names.getOrDefault(c.getUserId(), "已删除用户 " + c.getUserId()));
            out.add(m);
        }
        return out;
    }
}
