package com.labsys.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labsys.common.Result;
import com.labsys.dto.PageVO;
import com.labsys.dto.TicketRequest;
import com.labsys.entity.Ticket;
import com.labsys.mapper.RoleMapper;
import com.labsys.mapper.TicketMapper;
import com.labsys.service.TicketService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TicketController {

    private final TicketMapper ticketMapper;
    private final TicketService ticketService;
    private final RoleMapper roleMapper;

    public TicketController(TicketMapper ticketMapper, TicketService ticketService, RoleMapper roleMapper) {
        this.ticketMapper = ticketMapper;
        this.ticketService = ticketService;
        this.roleMapper = roleMapper;
    }

    /** 4-2 提交报修：任何登录用户都能报 */
    @PostMapping("/tickets")
    public Result add(@RequestBody TicketRequest req, @RequestAttribute("userId") Long userId) {
        return ticketService.create(userId, req);
    }

    /** 我的报修 */
    @GetMapping("/tickets/mine")
    public Result mine(@RequestAttribute("userId") Long userId,
                       @RequestParam(defaultValue = "1") int pageNum,
                       @RequestParam(defaultValue = "10") int pageSize) {

        Page<Ticket> page = new Page<>(pageNum, pageSize);
        ticketMapper.selectPage(page, new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getReporterId, userId)
                .orderByDesc(Ticket::getId));

        return new Result(200, "ok", new PageVO<>(page.getTotal(), page.getRecords()));
    }

    /** 全部工单：管理员和实验员用，由 StaffInterceptor 把关 */
    @GetMapping("/tickets")
    public Result list(@RequestParam(defaultValue = "1") int pageNum,
                       @RequestParam(defaultValue = "10") int pageSize,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) Integer overdue) {

        Page<Ticket> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Ticket::getStatus, status);
        }
        if (overdue != null) {
            wrapper.eq(Ticket::getOverdue, overdue);
        }
        wrapper.orderByDesc(Ticket::getId);

        ticketMapper.selectPage(page, wrapper);
        return new Result(200, "ok", new PageVO<>(page.getTotal(), page.getRecords()));
    }

    /** 工单详情 + 处理流水，学生只能看自己的 */
    @GetMapping("/tickets/{id}")
    public Result detail(@PathVariable Long id,
                         @RequestAttribute("userId") Long userId) {
        return ticketService.detail(id, userId, isStaff(userId));
    }

    /** 4-3 状态流转，remark 会记进流水 */
    @PutMapping("/tickets/{id}/status")
    public Result changeStatus(@PathVariable Long id,
                               @RequestParam String to,
                               @RequestParam(required = false) String remark,
                               @RequestAttribute("userId") Long operatorId) {
        return ticketService.changeStatus(id, to, operatorId, remark);
    }

    private boolean isStaff(Long userId) {
        List<String> roles = roleMapper.findRoleCodesByUserId(userId);
        return roles.contains("ADMIN") || roles.contains("KEEPER");
    }
}
