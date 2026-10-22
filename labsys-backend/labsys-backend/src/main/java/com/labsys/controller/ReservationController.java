package com.labsys.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labsys.common.Result;
import com.labsys.dto.PageVO;
import com.labsys.dto.ReservationRequest;
import com.labsys.entity.Reservation;
import com.labsys.mapper.ReservationMapper;
import com.labsys.service.ReservationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ReservationController {

    /** 终态：流程已经走完，归档处理，不再出现在主界面 */
    private static final List<String> ARCHIVED_STATUS = Arrays.asList("RETURNED", "CANCELLED", "REJECTED");

    private final ReservationMapper reservationMapper;
    private final ReservationService reservationService;

    public ReservationController(ReservationMapper reservationMapper, ReservationService reservationService) {
        this.reservationMapper = reservationMapper;
        this.reservationService = reservationService;
    }

    /**
     * 提交预约。这里只做参数格式校验，
     * 真正的“锁设备 + 查冲突 + 插入”在 ReservationService 里，因为要在一个事务内完成。
     */
    @PostMapping("/reservations")
    public Result add(@RequestBody ReservationRequest req,
                      @RequestAttribute("userId") Long userId) {

        if (req.getDeviceId() == null || req.getStartTime() == null || req.getEndTime() == null) {
            return new Result(400, "设备、开始时间、结束时间都不能为空", null);
        }
        if (!req.getStartTime().isBefore(req.getEndTime())) {
            return new Result(400, "开始时间必须早于结束时间", null);
        }
        if (req.getStartTime().isBefore(LocalDateTime.now())) {
            return new Result(400, "不能预约已经过去的时间", null);
        }

        return reservationService.create(userId, req);
    }

    /**
     * 我的预约：不分角色，一律按 token 里的 userId 过滤。
     * archived 的口径和下面的 list() 完全一致：终态算归档，默认不返回，
     * 否则学生的列表会越用越长，正在用的那条反而要往下翻才找得到。
     */
    @GetMapping("/reservations/mine")
    public Result mine(@RequestAttribute("userId") Long userId,
                       @RequestParam(defaultValue = "1") int pageNum,
                       @RequestParam(defaultValue = "10") int pageSize,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) Boolean archived) {

        Page<Reservation> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Reservation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Reservation::getUserId, userId);

        if (status != null && !status.isEmpty()) {
            wrapper.eq(Reservation::getStatus, status);
        } else if (Boolean.TRUE.equals(archived)) {
            wrapper.in(Reservation::getStatus, ARCHIVED_STATUS);
        } else {
            wrapper.notIn(Reservation::getStatus, ARCHIVED_STATUS);
        }

        wrapper.orderByDesc(Reservation::getStartTime)
                .orderByDesc(Reservation::getId);

        reservationMapper.selectPage(page, wrapper);
        return new Result(200, "ok", new PageVO<>(page.getTotal(), page.getRecords()));
    }

    /**
     * 全部预约。默认只返回“还在流转中”的，
     * 已归还 / 已取消 / 已拒绝 这三个终态算归档，挪到 archived=true 里查。
     * 这样管理员的主界面永远是待处理的，不会被历史记录淹没。
     */
    @GetMapping("/reservations")
    public Result list(@RequestParam(defaultValue = "1") int pageNum,
                       @RequestParam(defaultValue = "10") int pageSize,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) Long deviceId,
                       @RequestParam(required = false) Boolean archived) {

        Page<Reservation> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Reservation> wrapper = new LambdaQueryWrapper<>();

        if (status != null && !status.isEmpty()) {
            // 显式指定状态时以它为准，比如归档页里只筛「已归还」
            wrapper.eq(Reservation::getStatus, status);
        } else if (Boolean.TRUE.equals(archived)) {
            wrapper.in(Reservation::getStatus, ARCHIVED_STATUS);
        } else {
            wrapper.notIn(Reservation::getStatus, ARCHIVED_STATUS);
        }

        if (deviceId != null) {
            wrapper.eq(Reservation::getDeviceId, deviceId);
        }
        wrapper.orderByDesc(Reservation::getStartTime)
                .orderByDesc(Reservation::getId);

        reservationMapper.selectPage(page, wrapper);
        return new Result(200, "ok", new PageVO<>(page.getTotal(), page.getRecords()));
    }

    /** 改状态：审批、拒绝、取消、借出、归还都走这一个接口，合法流转由状态机把关 */
    @PutMapping("/reservations/{id}/status")
    public Result changeStatus(@PathVariable Long id,
                               @RequestParam String to,
                               @RequestAttribute("userId") Long operatorId) {
        return reservationService.changeStatus(id, to, operatorId);
    }

    /**
     * 学生取消自己的预约。
     * 故意不放在 /status 那个路径下：那条路径归 StaffInterceptor 管，学生进不去。
     */
    @PutMapping("/reservations/{id}/cancel")
    public Result cancel(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        return reservationService.cancel(id, userId);
    }

    /**
     * 借用人申请归还。状态会变成 RETURNING（待管理员确认），设备暂时仍占着，
     * 管理员在“预约管理”里确认后才真正释放。
     */
    @PutMapping("/reservations/{id}/return")
    public Result requestReturn(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        return reservationService.requestReturn(id, userId);
    }

    /** 待确认归还的条数，给管理员界面顶部提示用 */
    @GetMapping("/reservations/returning-count")
    public Result returningCount() {
        return new Result(200, "ok", reservationService.countReturning());
    }
}
