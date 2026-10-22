package com.labsys.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labsys.common.Result;
import com.labsys.dto.PageVO;
import com.labsys.entity.OperLog;
import com.labsys.mapper.OperLogMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LogController {

    private final OperLogMapper operLogMapper;

    public LogController(OperLogMapper operLogMapper) {
        this.operLogMapper = operLogMapper;
    }

    @GetMapping("/logs")
    public Result list(@RequestParam(defaultValue = "1") int pageNum,
                       @RequestParam(defaultValue = "10") int pageSize,
                       @RequestParam(required = false) String keyword) {
        Page<OperLog> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<OperLog> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(OperLog::getUsername, keyword)
                    .or()
                    .like(OperLog::getPath, keyword));
        }
        wrapper.orderByDesc(OperLog::getCreatedAt).orderByDesc(OperLog::getId);

        operLogMapper.selectPage(page, wrapper);
        return new Result(200, "ok", new PageVO<>(page.getTotal(), page.getRecords()));
    }
}
