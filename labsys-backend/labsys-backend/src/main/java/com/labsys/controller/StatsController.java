package com.labsys.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labsys.common.Result;
import com.labsys.entity.Ticket;
import com.labsys.mapper.DeviceMapper;
import com.labsys.mapper.ReservationMapper;
import com.labsys.mapper.StatsMapper;
import com.labsys.mapper.TicketMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class StatsController {

    private final StatsMapper statsMapper;
    private final DeviceMapper deviceMapper;
    private final ReservationMapper reservationMapper;
    private final TicketMapper ticketMapper;

    public StatsController(StatsMapper statsMapper,
                           DeviceMapper deviceMapper,
                           ReservationMapper reservationMapper,
                           TicketMapper ticketMapper) {
        this.statsMapper = statsMapper;
        this.deviceMapper = deviceMapper;
        this.reservationMapper = reservationMapper;
        this.ticketMapper = ticketMapper;
    }

    /**
     * 4-6 看板数据一把返回。
     * 拆成多个接口会让前端发 5 次请求，而这些都是同一次刷新要用的数据，
     * 合并成一个反而更快、也更不容易出现"一半新一半旧"。
     */
    @GetMapping("/stats/overview")
    public Result overview() {

        Map<String, Object> data = new HashMap<>();

        data.put("deviceStatus", statsMapper.deviceStatusCount());
        data.put("borrowRanking", statsMapper.borrowRanking());
        data.put("faultRanking", statsMapper.faultRanking());
        data.put("ticketStatus", statsMapper.ticketStatusCount());
        data.put("ticketTrend", statsMapper.ticketTrend(LocalDateTime.now().minusDays(6)));

        Map<String, Object> summary = new HashMap<>();
        summary.put("deviceTotal", deviceMapper.selectCount(null));
        summary.put("reservationTotal", reservationMapper.selectCount(null));
        summary.put("ticketTotal", ticketMapper.selectCount(null));
        summary.put("ticketPending", ticketMapper.selectCount(
                new LambdaQueryWrapper<Ticket>().eq(Ticket::getStatus, "PENDING")));
        summary.put("ticketOverdue", ticketMapper.selectCount(
                new LambdaQueryWrapper<Ticket>().eq(Ticket::getOverdue, 1)));
        data.put("summary", summary);

        return new Result(200, "ok", data);
    }
}
