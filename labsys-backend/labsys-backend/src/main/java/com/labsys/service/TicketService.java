package com.labsys.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labsys.common.Result;
import com.labsys.dto.TicketRequest;
import com.labsys.entity.Device;
import com.labsys.entity.Ticket;
import com.labsys.entity.TicketLog;
import com.labsys.mapper.DeviceMapper;
import com.labsys.mapper.TicketLogMapper;
import com.labsys.mapper.TicketMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TicketService {

    /**
     * 工单状态机。和预约那个一样，只允许表里列出的流转，
     * 其余一律拒绝 —— 这样“已解决的工单被改回待受理”这类脏数据进不来。
     *
     *   PENDING 待受理 ──accept──> ACCEPTED 处理中 ──resolve──> RESOLVED 已解决
     *        └──────reject──> REJECTED 已驳回
     */
    private static final Map<String, List<String>> TRANSITIONS = new HashMap<>();

    static {
        TRANSITIONS.put("PENDING", Arrays.asList("ACCEPTED", "REJECTED"));
        TRANSITIONS.put("ACCEPTED", Arrays.asList("RESOLVED", "REJECTED"));
        TRANSITIONS.put("RESOLVED", Collections.emptyList());
        TRANSITIONS.put("REJECTED", Collections.emptyList());
    }

    private static final List<String> PRIORITIES = Arrays.asList("LOW", "NORMAL", "HIGH", "URGENT");

    private final TicketMapper ticketMapper;
    private final TicketLogMapper ticketLogMapper;
    private final DeviceMapper deviceMapper;
    private final TransactionTemplate txTemplate;

    /** 超过多少小时没被受理/处理，就标记为超时催办 */
    @Value("${labsys.ticket-overdue-hours:24}")
    private int overdueHours;

    public TicketService(TicketMapper ticketMapper,
                         TicketLogMapper ticketLogMapper,
                         DeviceMapper deviceMapper,
                         PlatformTransactionManager transactionManager) {
        this.ticketMapper = ticketMapper;
        this.ticketLogMapper = ticketLogMapper;
        this.deviceMapper = deviceMapper;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    /** 4-2 提交报修 */
    public Result create(Long userId, TicketRequest req) {

        if (req.getDeviceId() == null || req.getFaultDesc() == null || req.getFaultDesc().trim().isEmpty()) {
            return new Result(400, "设备和故障描述都不能为空", null);
        }
        if (req.getFaultDesc().length() > 500) {
            return new Result(400, "故障描述最多 500 字", null);
        }
        if (req.getPriority() != null && !PRIORITIES.contains(req.getPriority())) {
            return new Result(400, "优先级只能是 LOW/NORMAL/HIGH/URGENT", null);
        }

        return txTemplate.execute(status -> {

            Device device = deviceMapper.selectById(req.getDeviceId());
            if (device == null) {
                return new Result(404, "设备不存在", null);
            }

            Ticket t = new Ticket();
            t.setDeviceId(req.getDeviceId());
            t.setReporterId(userId);
            t.setFaultDesc(req.getFaultDesc().trim());
            t.setPriority(req.getPriority() == null ? "NORMAL" : req.getPriority());
            t.setStatus("PENDING");
            t.setOverdue(0);
            ticketMapper.insert(t);

            // 报修本身也是一次状态变化（从“不存在”到 PENDING），记进流水时间线
            writeLog(t.getId(), null, "PENDING", userId, "提交报修");

            return new Result(200, "ok", t.getId());
        });
    }

    /** 4-3 状态流转 + 4-4 记流水 */
    public Result changeStatus(Long id, String to, Long operatorId, String remark) {

        if (to == null || to.isEmpty()) {
            return new Result(400, "目标状态不能为空", null);
        }

        return txTemplate.execute(status -> {

            Ticket t = ticketMapper.selectById(id);
            if (t == null) {
                return new Result(404, "工单不存在", null);
            }

            String from = t.getStatus();
            List<String> allowed = TRANSITIONS.getOrDefault(from, Collections.emptyList());
            if (!allowed.contains(to)) {
                return new Result(400, "不能从 " + from + " 改成 " + to, null);
            }

            t.setStatus(to);
            if ("ACCEPTED".equals(to) && t.getHandlerId() == null) {
                t.setHandlerId(operatorId);
            }
            if ("RESOLVED".equals(to)) {
                t.setResolvedAt(LocalDateTime.now());
            }
            ticketMapper.updateById(t);

            // 处理完就不该再挂着超时标记
            writeLog(id, from, to, operatorId, remark);

            syncDeviceStatus(t.getDeviceId(), from, to);

            return new Result(200, "ok", null);
        });
    }

    /**
     * 工单状态联动设备状态，让"报修 → 停机 → 修好 → 可约"这条链路闭合。
     *
     * 两个刻意的取舍：
     *   ① 提交报修（PENDING）时不动设备 —— 还没人确认，学生一句话就让一台机器下线太草率；
     *   ② 只动"该动的那一侧"：设备正在被借出（BORROWED）时受理工单，不会把它改成 REPAIRING，
     *      否则借用人手里的设备状态就丢了。等他还回来、管理员再点受理即可。
     *
     * 恢复（→ AVAILABLE）还额外要求 from 是 ACCEPTED：只有这条工单真的把设备置成过维修中，
     * 才有资格把它放回去。否则「另一条工单刚把设备置成维修中、这条工单被驳回」会把设备错误地放回在库。
     */
    private void syncDeviceStatus(Long deviceId, String from, String to) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            return;
        }
        String current = device.getStatus();

        if ("ACCEPTED".equals(to) && "AVAILABLE".equals(current)) {
            updateDeviceStatus(deviceId, "REPAIRING");
        } else if ("ACCEPTED".equals(from) && "REPAIRING".equals(current)
                && ("RESOLVED".equals(to) || "REJECTED".equals(to))) {
            updateDeviceStatus(deviceId, "AVAILABLE");
        }
    }

    private void updateDeviceStatus(Long deviceId, String status) {
        Device d = new Device();
        d.setId(deviceId);
        d.setStatus(status);
        deviceMapper.updateById(d);
    }

    /** 工单详情：含完整处理流水 */
    public Result detail(Long id, Long userId, boolean isStaff) {

        Ticket t = ticketMapper.selectById(id);
        if (t == null) {
            return new Result(404, "工单不存在", null);
        }
        // 学生只能看自己报的工单，管理员和实验员能看全部
        if (!isStaff && !t.getReporterId().equals(userId)) {
            return new Result(403, "只能查看自己提交的工单", null);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("ticket", t);
        data.put("logs", ticketLogMapper.findByTicketId(id));
        return new Result(200, "ok", data);
    }

    /** 4-5 定时任务：把长时间没受理/没处理的工单标记为超时 */
    public int markOverdue() {
        Integer n = txTemplate.execute(status -> {

            LocalDateTime deadline = LocalDateTime.now().minusHours(overdueHours);

            List<Ticket> list = ticketMapper.selectList(new LambdaQueryWrapper<Ticket>()
                    .in(Ticket::getStatus, Arrays.asList("PENDING", "ACCEPTED"))
                    .eq(Ticket::getOverdue, 0)
                    .lt(Ticket::getCreatedAt, deadline));

            List<Ticket> toUpdate = new ArrayList<>();
            for (Ticket t : list) {
                t.setOverdue(1);
                toUpdate.add(t);
            }
            for (Ticket t : toUpdate) {
                ticketMapper.updateById(t);
            }
            return toUpdate.size();
        });
        return n == null ? 0 : n;
    }

    private void writeLog(Long ticketId, String from, String to, Long operatorId, String remark) {
        TicketLog log = new TicketLog();
        log.setTicketId(ticketId);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperatorId(operatorId);
        log.setRemark(remark);
        ticketLogMapper.insert(log);
    }
}
