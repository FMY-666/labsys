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
import java.time.LocalTime;
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

    /**
     * "这个人手上还有一件没结束的事"的状态。
     * 比 OCCUPYING 多两个：RETURNING（等确认归还）和 OVERDUE（超时未还），
     * 它们虽然不再挡别人的时段，但仍然算这个人占着一台设备。
     */
    private static final List<String> ACTIVE_STATUS =
            Arrays.asList("PENDING", "APPROVED", "BORROWED", "RETURNING", "OVERDUE");

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

    /** 实验室最晚到几点结束。前端只列到 21:00，但前端的不算数 */
    @Value("${labsys.reservation.close-hour:22}")
    private int closeHour;

    /** 一个人最多同时保留几条进行中的预约 */
    @Value("${labsys.max-active-reservations:3}")
    private int maxActiveReservations;

    /** 超时未还的记录再过多少天自动结案，避免永久挂在「进行中」 */
    @Value("${labsys.overdue-close-days:3}")
    private int overdueCloseDays;

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

        // 这几条都属于业务规则，放在锁之前只为早失败；真正需要一致性的检查在下面抢锁之后
        if (req.getPurpose() == null || req.getPurpose().trim().isEmpty()) {
            return new Result(400, "请填写借出理由", null);
        }
        if (req.getPurpose().length() > 255) {
            return new Result(400, "借出理由最多 255 字", null);
        }
        // 跨天的预约会让"每天 22:00 关门"这条规则失去意义，直接不允许
        if (!req.getStartTime().toLocalDate().equals(req.getEndTime().toLocalDate())) {
            return new Result(400, "预约不能跨天，请分开提交", null);
        }
        if (req.getEndTime().toLocalTime().isAfter(LocalTime.of(closeHour, 0))) {
            return new Result(400, "实验室 " + closeHour + ":00 关门，结束时间不能晚于此", null);
        }

        Device device = lockDeviceRow
                ? deviceMapper.selectByIdForUpdate(req.getDeviceId())
                : deviceMapper.selectById(req.getDeviceId());

        if (device == null) {
            return new Result(404, "设备不存在", null);
        }
        if ("SCRAPPED".equals(device.getStatus()) || "REPAIRING".equals(device.getStatus())) {
            return new Result(400, "这台设备当前不可预约", null);
        }

        // 同一个人在同一时段不能同时约两台设备——他不可能同时在两个实验室做实验
        Long mine = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .in(Reservation::getStatus, ACTIVE_STATUS)
                .lt(Reservation::getStartTime, req.getEndTime())
                .gt(Reservation::getEndTime, req.getStartTime()));
        if (mine != null && mine > 0) {
            return new Result(409, "你在同一时段已经有一条预约了", null);
        }

        // 一个人手上不能无限堆着"待审批 + 使用中"，否则他可以把所有设备都占住
        Long active = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .in(Reservation::getStatus, ACTIVE_STATUS));
        if (active != null && active >= maxActiveReservations) {
            return new Result(400,
                    "同时最多保留 " + maxActiveReservations + " 条进行中的预约，先处理掉手上的再约", null);
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
        r.setPurpose(req.getPurpose().trim());
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

            // 借出必须已经到点。校验放在改状态之前，否则 400 返回时状态已经被改了（这个事务没抛异常，会正常提交）
            if ("BORROWED".equals(to) && LocalDateTime.now().isBefore(r.getStartTime())) {
                return new Result(400, "还没到预约开始时间（"
                        + r.getStartTime().toString().replace('T', ' ') + "），不能提前借出", null);
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

    /**
     * 续借：把自己这次预约往后延几小时。
     *
     * 只允许 APPROVED / BORROWED：还没批的预约"续借"没有意义，重新提交更快；
     * 已经归还或超时的更不该续。
     * 延长是"改结束时间"，所以冲突检测要看 (原结束时间, 新结束时间) 这一段有没有人占着 ——
     * 不查的话会直接和后面那位的预约重叠。
     */
    public Result extend(Long id, Long userId, int hours) {
        if (hours < 1 || hours > 8) {
            return new Result(400, "一次最多延长 8 小时", null);
        }

        return txTemplate.execute(status -> {
            Reservation r = reservationMapper.selectById(id);
            if (r == null) {
                return new Result(404, "预约不存在", null);
            }
            if (!r.getUserId().equals(userId)) {
                return new Result(403, "只能续借自己的预约", null);
            }
            if (!"APPROVED".equals(r.getStatus()) && !"BORROWED".equals(r.getStatus())) {
                return new Result(400, "只有已通过或使用中的预约才能续借，当前是 " + r.getStatus(), null);
            }

            LocalDateTime newEnd = r.getEndTime().plusHours(hours);

            if (!newEnd.toLocalDate().equals(r.getStartTime().toLocalDate())) {
                return new Result(400, "续借不能跨天，最多延到当天 " + closeHour + ":00", null);
            }
            if (newEnd.toLocalTime().isAfter(LocalTime.of(closeHour, 0))) {
                return new Result(400, "实验室 " + closeHour + ":00 关门，续不到那么晚", null);
            }
            if (Duration.between(r.getStartTime(), newEnd).toHours() > CreditService.BASE_MAX_HOURS) {
                return new Result(400, "单次预约最长 " + CreditService.BASE_MAX_HOURS + " 小时", null);
            }

            // 只查"后面这一段"：start < 新结束 && end > 原结束
            Long conflicts = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                    .eq(Reservation::getDeviceId, r.getDeviceId())
                    .in(Reservation::getStatus, OCCUPYING)
                    .lt(Reservation::getStartTime, newEnd)
                    .gt(Reservation::getEndTime, r.getEndTime()));
            if (conflicts != null && conflicts > 0) {
                return new Result(409, "后面的时段已经有人约了这台设备，续不了", null);
            }

            r.setEndTime(newEnd);
            reservationMapper.updateById(r);
            return new Result(200, "已延长到 " + newEnd.toLocalTime().toString().substring(0, 5), null);
        });
    }

    /** 管理员界面顶部的“待确认归还”条数 */
    public long countReturning() {        Long n = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
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

    /**
     * 清理永远走不到终态的记录。不清理的话「进行中」列表只增不减：
     *   - 过期的 PENDING：时段都过去了才有人想起来审批，批了也没意义，直接作废
     *   - 挂太久的 OVERDUE：设备早就释放了，但学生不点「申请归还」就永远是 OVERDUE
     * 两者都不删记录，只改状态 —— 历史要留痕，只是不该占着「进行中」。
     */
    public int closeStale() {
        Integer n = txTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now();

            List<Reservation> expired = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                    .eq(Reservation::getStatus, "PENDING")
                    .lt(Reservation::getEndTime, now));
            for (Reservation r : expired) {
                r.setStatus("CANCELLED");
                reservationMapper.updateById(r);
            }

            List<Reservation> abandoned = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                    .eq(Reservation::getStatus, "OVERDUE")
                    .lt(Reservation::getEndTime, now.minusDays(overdueCloseDays)));
            for (Reservation r : abandoned) {
                r.setStatus("RETURNED");
                if (r.getReturnedAt() == null) {
                    r.setReturnedAt(now);
                }
                reservationMapper.updateById(r);
            }

            return expired.size() + abandoned.size();
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
