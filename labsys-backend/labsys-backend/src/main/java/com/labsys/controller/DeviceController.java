package com.labsys.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labsys.common.Result;
import com.labsys.dto.PageVO;
import com.labsys.entity.Device;
import com.labsys.mapper.DeviceMapper;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DeviceController {

    private final DeviceMapper deviceMapper;

    public DeviceController(DeviceMapper deviceMapper) {
        this.deviceMapper = deviceMapper;
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

    @PostMapping("/devices")
    public Result add(@RequestBody Device device) {
        device.setId(null);
        device.setCreatedAt(null);

        if (device.getStatus() == null || device.getStatus().isEmpty()) {
            device.setStatus("AVAILABLE");
        }

        deviceMapper.insert(device);
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
