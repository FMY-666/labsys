package com.labsys.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labsys.common.Result;
import com.labsys.entity.DeviceDict;
import com.labsys.mapper.DeviceDictMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 设备分类 / 存放位置的下拉数据。只读，写入由保存设备时自动带进来。 */
@RestController
@RequestMapping("/api")
public class DeviceDictController {

    private final DeviceDictMapper dictMapper;

    public DeviceDictController(DeviceDictMapper dictMapper) {
        this.dictMapper = dictMapper;
    }

    @GetMapping("/device-dict")
    public Result list(@RequestParam String type) {
        List<DeviceDict> list = dictMapper.selectList(new LambdaQueryWrapper<DeviceDict>()
                .eq(DeviceDict::getType, type)
                .orderByAsc(DeviceDict::getId));
        return new Result(200, "ok", list);
    }
}
