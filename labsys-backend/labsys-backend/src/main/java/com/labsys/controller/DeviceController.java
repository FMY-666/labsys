package com.labsys.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labsys.common.Result;
import com.labsys.dto.PageVO;
import com.labsys.entity.Device;
import com.labsys.entity.DeviceDict;
import com.labsys.entity.Reservation;
import com.labsys.entity.Ticket;
import com.labsys.mapper.DeviceDictMapper;
import com.labsys.mapper.DeviceMapper;
import com.labsys.mapper.ReservationMapper;
import com.labsys.mapper.TicketMapper;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DeviceController {

    /** 真正"借出去过"的状态。APPROVED 还没拿到手，PENDING 更不算 */
    private static final List<String> EVER_BORROWED =
            Arrays.asList("BORROWED", "RETURNING", "RETURNED", "OVERDUE");

    private final DeviceMapper deviceMapper;
    private final ReservationMapper reservationMapper;
    private final TicketMapper ticketMapper;
    private final DeviceDictMapper dictMapper;

    public DeviceController(DeviceMapper deviceMapper,
                            ReservationMapper reservationMapper,
                            TicketMapper ticketMapper,
                            DeviceDictMapper dictMapper) {
        this.deviceMapper = deviceMapper;
        this.reservationMapper = reservationMapper;
        this.ticketMapper = ticketMapper;
        this.dictMapper = dictMapper;
    }

    /**
     * 把这次填的分类/位置记进字典。新增设备时前端允许直接输入新值，
     * 记下来以后就能在下来框里选，不用维护第三个"字典管理"页面。
     * 并发时可能撞唯一索引，忽略即可 —— 说明别人已经先记过同一个值了。
     */
    private void rememberDict(String type, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        String v = value.trim();
        Long exists = dictMapper.selectCount(new LambdaQueryWrapper<DeviceDict>()
                .eq(DeviceDict::getType, type)
                .eq(DeviceDict::getValue, v));
        if (exists != null && exists > 0) {
            return;
        }
        try {
            DeviceDict d = new DeviceDict();
            d.setType(type);
            d.setValue(v);
            dictMapper.insert(d);
        } catch (Exception ignore) {
            // 唯一索引冲突：别的请求刚好也插了，忽略
        }
    }

    @GetMapping("/devices/count")
    public Result count() {
        return new Result(200, "ok", deviceMapper.selectCount(null));
    }

    @GetMapping("/devices")
    public Result list(@RequestParam(defaultValue = "1") int pageNum,
                       @RequestParam(defaultValue = "10") int pageSize,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String status) {
        Page<Device> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Device::getDeviceNo, keyword)
                    .or()
                    .like(Device::getName, keyword));
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Device::getStatus, status);
        }

        deviceMapper.selectPage(page, wrapper);
        return new Result(200, "ok", new PageVO<>(page.getTotal(), page.getRecords()));
    }

    @GetMapping("/devices/{id}")
    public Result getOne(@PathVariable Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            return new Result(404,"设备不存在",null);
        }
        return new Result(200,"ok",device);
    }

    /**
     * 一台设备的历史：被谁借过、修过几次。
     * 台账页的视角是"这台机器怎么样"，光有当前状态不够 —— 管理员想知道的是它这个月被谁用过、坏过几回。
     * 和预约/工单列表不同，这里的查询是"按设备"，所以直接从这两张表按 device_id 捞。
     */
    @GetMapping("/devices/{id}/history")
    public Result history(@PathVariable Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            return new Result(404, "设备不存在", null);
        }

        List<Reservation> reservations = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getDeviceId, id)
                .orderByDesc(Reservation::getStartTime)
                .last("LIMIT 20"));

        List<Ticket> tickets = ticketMapper.selectList(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getDeviceId, id)
                .orderByDesc(Ticket::getCreatedAt)
                .last("LIMIT 20"));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("borrowCount", reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getDeviceId, id)
                .in(Reservation::getStatus, EVER_BORROWED)));
        stats.put("repairCount", ticketMapper.selectCount(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getDeviceId, id)));

        Map<String, Object> data = new HashMap<>();
        data.put("device", device);
        data.put("stats", stats);
        data.put("reservations", reservations);
        data.put("tickets", tickets);
        return new Result(200, "ok", data);
    }

    @PostMapping("/devices")
    public Result add(@RequestBody Device device) {
        device.setId(null);
        device.setCreatedAt(null);

        if (device.getStatus() == null || device.getStatus().isEmpty()) {
            device.setStatus("AVAILABLE");
        }

        deviceMapper.insert(device);
        rememberDict(DeviceDict.CATEGORY, device.getCategory());
        rememberDict(DeviceDict.LOCATION, device.getLocation());
        return new Result(200, "ok", null);
    }

    @PutMapping("/devices/{id}")
    public Result update(@PathVariable Long id, @RequestBody Device device) {
        Device exist = deviceMapper.selectById(id);
        if (exist == null) {
            return new Result(404, "设备不存在", null);
        }

        device.setId(id);                  // 以路径上的 id 为准，忽略请求体里的 id
        deviceMapper.updateById(device);
        rememberDict(DeviceDict.CATEGORY, device.getCategory());
        rememberDict(DeviceDict.LOCATION, device.getLocation());
        return new Result(200, "ok", null);
    }

    @DeleteMapping("/devices/{id}")
    public Result delete(@PathVariable Long id) {
        int rows = deviceMapper.deleteById(id);
        if (rows == 0) {
            return new Result(404, "设备不存在", null);
        }
        return new Result(200, "ok", null);
    }

}
