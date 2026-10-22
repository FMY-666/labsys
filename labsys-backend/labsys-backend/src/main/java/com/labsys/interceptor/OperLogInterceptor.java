package com.labsys.interceptor;

import com.labsys.entity.OperLog;
import com.labsys.entity.User;
import com.labsys.mapper.OperLogMapper;
import com.labsys.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 操作日志：写操作结束后落一条记录。
 * 只在 /api 下注册，且必须排在登录校验之后，userId 才拿得到。
 */
@Component
public class OperLogInterceptor implements HandlerInterceptor {

    private static final String START_ATTR = "operLogStartAt";

    private final OperLogMapper operLogMapper;
    private final UserMapper userMapper;

    public OperLogInterceptor(OperLogMapper operLogMapper, UserMapper userMapper) {
        this.operLogMapper = operLogMapper;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        String method = request.getMethod();
        // 查询不记，只记改动过数据的请求
        boolean isWrite = "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method);
        if (!isWrite) {
            return;
        }

        Object start = request.getAttribute(START_ATTR);
        int costMs = start instanceof Long ? (int) (System.currentTimeMillis() - (Long) start) : 0;

        OperLog log = new OperLog();
        Long userId = (Long) request.getAttribute("userId");
        if (userId != null) {
            log.setUserId(userId);
            User user = userMapper.selectById(userId);
            if (user != null) {
                log.setUsername(user.getUsername());
            }
        } else {
            log.setUserId(0L);
        }

        log.setMethod(method);
        log.setPath(request.getRequestURI());
        log.setIp(clientIp(request));
        log.setSuccess(ex != null || response.getStatus() >= 400 ? 0 : 1);
        log.setCostMs(costMs);

        try {
            operLogMapper.insert(log);
        } catch (Exception ignored) {
            // 记日志失败不能反过来影响业务结果
        }
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        if (ip == null) {
            return null;
        }
        return ip.length() > 50 ? ip.substring(0, 50) : ip;
    }
}
