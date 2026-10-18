package com.labsys.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 参数格式不对，例如 GET /api/devices/abc（路径 id 必须是数字）。
     * 返回 400，且不把 Spring 的类型转换异常原文返回给客户端。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数格式错误: {}", e.getMessage());
        return new Result(400, "参数格式不对", null);
    }

    /**
     * 唯一键冲突：新增设备时 device_no 与 uk_device_no 重复。
     * 数据库返回 1062，MyBatis-Spring 翻译成 DuplicateKeyException；
     * 不单独处理会落到兜底分支，客户端只能看到"服务器内部错误"。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一键冲突: {}", e.getMessage());
        return new Result(409, "设备编号已存在", null);
    }

    /**
     * 兜底处理没有被上面单独捕获的异常。
     *
     * 日志保留完整堆栈和原始 message，响应只返回笼统提示，
     * 避免类名、包名、参数名、SQL 等内部信息外泄。
     *
     * 约定：HTTP 状态码统一 200，业务状态放在响应体的 code 字段。
     */
    @ExceptionHandler(Exception.class)
    public Result handle(Exception e) {
        log.error("系统异常", e);
        return new Result(500, "服务器内部错误", null);
    }
}
