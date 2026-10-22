package com.labsys.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labsys.common.Result;
import com.labsys.dto.ReservationRequest;
import com.labsys.entity.Device;
import com.labsys.entity.Reservation;
import com.labsys.mapper.DeviceMapper;
import com.labsys.mapper.ReservationMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReservationService {

    /** 仍然占用时段的三个状态；被拒、已取消、已归还的不再挡别人 */
    private static final List<String> OCCUPYING = Arrays.asList("PENDING", "APPROVED", "BORROWED");

    /** 状态机：当前状态 -> 允许流转到的状态。不在表里的流转一律拒绝 */
    private static final Map<String, List<String>> TRANSITIONS = new HashMap<>();

    static {
        TRANSITIONS.put("PENDING", Arrays.asList("APPROVED", "REJECTED", "CANCELLED"));
        TRANSITIONS.put("APPROVED", Arrays.asList("BORROWED", "CANCELLED"));
        // RETURNING = 借用人已经点过归还、等管理员确认。
        // 管理员也可以跳过这一步直接 RETURNED（当场收回设备的情况）。
        TRANSITIONS.put("BORROWED", Arrays.asList("RETURNING", "RETURNED"));
        TRANSITIONS.put("OVERDUE", Arrays.asList("RETURNING", "RETURNED"));
        TRANSITIONS.put("RETURNING", Arrays.asList("RETURNED"));
        TRANSITIONS.put("RETURNED", Collections.emptyList());
        TRANSITIONS.put("REJECTED", Collections.emptyList());
        TRANSITIONS.put("CANCELLED", Collections.emptyList());
    }

    private static final String LOCK_PREFIX = "lock:reservation:device:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);

    /** 超过结束时间多久算一次超时违规。留 30 分钟缓冲，避免管理员确认晚了几分钟就罚人 */
    private static final long OVERDUE_GRACE_MINUTES = 30;

    /**
     * 释放锁必须“先判断是不是自己的、再删”，两步要原子完成。
     * 如果直接 del，可能删掉别人刚抢到的锁（自己持锁超时后，别人拿到锁，自己这才执行到 finally）。
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final ReservationMapper reservationMapper;
    private final DeviceMapper deviceMapper;
    private final StringRedisTemplate redisTemplate;
    private final TransactionTemplate txTemplate;
    private final CreditService creditService;

    /** db = 数据库行锁；redis = Redis 分布式锁。用配置切换，方便对比两种方案 */
    @Value("${labsys.lock-mode:db}")
    private String lockMode;

    public ReservationService(ReservationMapper reservationMapper,
                              DeviceMapper deviceMapper,
                              StringRedisTemplate redisTemplate,
                              PlatformTransactionManager transactionManager,
                              CreditService creditService) {
        this.reservationMapper = reservationMapper;
        this.deviceMapper = deviceMapper;
        this.redisTemplate = redisTemplate;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.creditService = creditService;
    }

    /**
     * 提交预约。两种防超借方案在这里分叉：
     *   db    —— 事务内先 SELECT ... FOR UPDATE 锁住设备行，让并发请求排队
     *   redis —— 事务外先抢一个 key，抢不到直接告诉调用方“稍等”，抢到才进事务
     * 事务一律用 TransactionTemplate 手动控制，不用 @Transactional 注解 ——
     * 因为 Redis 锁必须在事务提交之后才释放，注解的事务边界（方法返回时提交）盖不住锁的 finally。
     */
    public Result create(Long userId, ReservationRequest req) {

        // 准入检查放在抢锁之前：被禁约的人不该去排这个队，也省得为他锁一次设备行
        Result denied = creditService.checkBooking(userId, req.getStartTime(), req.getEndTime());
        if (denied != null) {
            return denied;
        }

        if ("redis".equals(lockMode)) {
            String key = LOCK_PREFIX + req.getDeviceId();
            String token = UUID.randomUUID().toString();

            // setIfAbsent 就是 Redis 的 SET key value NX EX seconds，一条命令同时完成“不存在才设置”和“设置过期”
            Boolean got = redisTemplate.opsForValue().setIfAbsent(key, token, LOCK_TTL);
            if (!Boolean.TRUE.equals(got)) {
                return new Result(409, "这台设备正在被其他人预约，请稍后重试", null);
            }
            try {
                return txTemplate.execute(status -> createOnce(userId, req, false));
            } finally {
                redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
            }
        }

        return txTemplate.execute(status -> createOnce(userId, req, true));
    }

    /**
     * @param lockDeviceRow true 时用 FOR UPDATE 锁设备行（数据库锁方案）；
     *                      false 表示并发已经由 Redis 锁挡住了，不需要再锁库
     */
    private Result createOnce(Long userId, ReservationRequest req, boolean lockDeviceRow) {

        Device device = lockDeviceRow
                ? deviceMapper.selectByIdForUpdate(req.getDeviceId())
                : deviceMapper.selectById(req.getDeviceId());

        if (device == null) {
            return new Result(404, "设备不存在", null);
        }
        if ("SCRAPPED".equals(device.getStatus()) || "REPAIRING".equals(device.getStatus())) {
            return new Result(400, "这台设备当前不可预约", null);
        }

        // 区间重叠：已有.start < 新.end && 已有.end > 新.start
        Long conflicts = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getDeviceId, req.getDeviceId())
                .in(Reservation::getStatus, OCCUPYING)
                .lt(Reservation::getStartTime, req.getEndTime())
                .gt(Reservation::getEndTime, req.getStartTime()));

        if (conflicts != null && conflicts > 0) {
            return new Result(409, "这个时段已经被别人预约了", null);
        }

        Reservation r = new Reservation();
        r.setUserId(userId);
        r.setDeviceId(req.getDeviceId());
        r.setStartTime(req.getStartTime());
        r.setEndTime(req.getEndTime());
        r.setPurpose(req.getPurpose());
        r.setStatus("PENDING");
        reservationMapper.insert(r);

        return new Result(200, "ok", r.getId());
    }

    /**
     * 改预约状态，按状态机校验。
     * 借出 (APPROVED -> BORROWED) 时把设备标成 BORROWED，归还时改回 AVAILABLE。
     */
    public Result changeStatus(Long id, String to, Long operatorId) {

        return txTemplate.execute(status -> {

            Reservation r = reservationMapper.selectById(id);
            if (r == null) {
                return new Result(404, "预约不存在", null);
            }

            String from = r.getStatus();
            List<String> allowed = TRANSITIONS.getOrDefault(from, Collections.emptyList());
            if (!allowed.contains(to)) {
                return new Result(400, "不能从 " + from + " 改成 " + to, null);
            }

            r.setStatus(to);
            // 只在第一次审批通过时记录审批人，后续流转不覆盖
            if ("APPROVED".equals(to) && r.getApproverId() == null) {
                r.setApproverId(operatorId);
            }
            // 管理员确认归还时记下时间，这个时间会显示在管理员界面
            if ("RETURNED".equals(to) && r.getReturnedAt() == null) {
                r.setReturnedAt(LocalDateTime.now());
            }
            reservationMapper.updateById(r);

            if ("BORROWED".equals(to)) {
                updateDeviceStatus(r.getDeviceId(), "BORROWED");
            } else if ("RETURNED".equals(to)) {
                // 到这一步设备才真正释放，别人可以接着约
                updateDeviceStatus(r.getDeviceId(), "AVAILABLE");
                // 按时归还加分：以借用人自己点“归还”的时间为准，不是管理员确认的时间。
                // 学生按点交了、管理员隔两小时才确认，不该算学生超时。
                LocalDateTime handIn = r.getReturnRequestedAt() != null
                        ? r.getReturnRequestedAt()
                        : LocalDateTime.now();
                if (!handIn.isAfter(r.getEndTime())) {
                    creditService.applyOnTime(r.getUserId());
                }
            }

            return new Result(200, "ok", null);
        });
    }

    /**
     * 学生取消自己的预约。
     * 单独开一个接口，是因为通用的状态流转接口被 StaffInterceptor 挡着（学生没有那个权限），
     * 但“约错了自己撤掉”是学生必须有的能力。这里自己校验归属，不依赖拦截器。
     */
    public Result cancel(Long id, Long userId) {
        return txTemplate.execute(status -> {
            Reservation r = reservationMapper.selectById(id);
            if (r == null) {
                return new Result(404, "预约不存在", null);
            }
            if (!r.getUserId().equals(userId)) {
                return new Result(403, "只能取消自己的预约", null);
            }
            if (!"PENDING".equals(r.getStatus())) {
                return new Result(400, "只有待审批的预约才能取消，当前是 " + r.getStatus(), null);
            }
            r.setStatus("CANCELLED");
            reservationMapper.updateById(r);
            return new Result(200, "ok", null);
        });
    }

    /**
     * 借用人申请归还。
     * 注意这里是“申请”不是“完成”：状态转成 RETURNING，记下申请时间，
     * 但设备先不释放 —— 要等管理员当面确认设备没问题，才真正变成 RETURNED。
     * 和 cancel 一样单独开接口，因为通用状态流转接口被 StaffInterceptor 挡着。
     */
    public Result requestReturn(Long id, Long userId) {
        return txTemplate.execute(status -> {
            Reservation r = reservationMapper.selectById(id);
            if (r == null) {
                return new Result(404, "预约不存在", null);
            }
            if (!r.getUserId().equals(userId)) {
                return new Result(403, "只能归还自己借的设备", null);
            }
            if (!"BORROWED".equals(r.getStatus()) && !"OVERDUE".equals(r.getStatus())) {
                String now = r.getStatus();
                if ("RETURNING".equals(now)) {
                    return new Result(400, "已经提交过归还申请了，等管理员确认", null);
                }
                return new Result(400, "只有使用中的设备才能归还，当前是 " + now, null);
            }
            r.setStatus("RETURNING");
            r.setReturnRequestedAt(LocalDateTime.now());
            reservationMapper.updateById(r);
            return new Result(200, "ok", null);
        });
    }

    /** 管理员界面顶部的“待确认归还”条数 */
    public long countReturning() {
        Long n = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getStatus, "RETURNING"));
        return n == null ? 0 : n;
    }

    /**
     * 3-10 定时任务，一次性做两件事：
     *   ① 已借出但过了结束时间 -> OVERDUE，并把设备放回 AVAILABLE
     *   ② 过了结束时间 30 分钟还没还 -> 记一次超时违规
     * 分开判是因为两者的时点不同：到点就该释放设备，但罚人要多等 30 分钟 ——
     * 管理员确认归还慢了、学生正在走回来的路上，都不该算违规。
     *
     * 只改状态、不删记录 —— 超时是既成事实，日志要留痕。
     * 为什么能释放设备：这些预约的时段已经过去了，不会和未来的预约重叠，
     * 所以真正卡住的只是设备那一个 status 字段。
     */
    public int releaseOverdue() {
        Integer n = txTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now();

            List<Reservation> list = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                    .eq(Reservation::getStatus, "BORROWED")
                    .lt(Reservation::getEndTime, now));

            for (Reservation r : list) {
                r.setStatus("OVERDUE");
                reservationMapper.updateById(r);
                updateDeviceStatus(r.getDeviceId(), "AVAILABLE");
            }

            // 只查 BORROWED / OVERDUE：RETURNING 是学生已经点了归还、等管理员确认，
            // 责任不在学生身上，不能罚。overdue_violation 保证一条预约只扣一次分。
            List<Reservation> late = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                    .in(Reservation::getStatus, Arrays.asList("BORROWED", "OVERDUE"))
                    .lt(Reservation::getEndTime, now.minusMinutes(OVERDUE_GRACE_MINUTES))
                    .eq(Reservation::getOverdueViolation, 0));

            for (Reservation r : late) {
                r.setOverdueViolation(1);
                reservationMapper.updateById(r);
                creditService.applyOverdue(r.getUserId());
            }

            return list.size();
        });
        return n == null ? 0 : n;
    }

    private void updateDeviceStatus(Long deviceId, String status) {
        Device d = new Device();
        d.setId(deviceId);
        d.setStatus(status);
        deviceMapper.updateById(d);
    }
}
