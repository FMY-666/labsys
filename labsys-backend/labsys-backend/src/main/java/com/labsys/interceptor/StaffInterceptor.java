package com.labsys.interceptor;

import com.labsys.mapper.RoleMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 员工校验：管理员或实验员才能访问。
 * 用在“看全部预约”和“改预约状态”这类需要管理视角的接口上，
 * 学生只能看自己的预约（那条接口按 userId 过滤，不需要这个拦截器）。
 * 必须在 AuthInterceptor 之后注册，userId 由后者写入 request。
 */
@Component
public class StaffInterceptor implements HandlerInterceptor {

    private final RoleMapper roleMapper;

    public StaffInterceptor(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // /api/reservations 这个路径同时承载两种语义：
        //   POST 是“提交预约”，任何登录用户都能用（学生的核心操作）
        //   GET  是“看全部预约”，只给管理员和实验员
        // 拦截器只能按路径匹配、区分不了方法，所以在这里把 POST 放行。
        if ("POST".equals(request.getMethod())) {
            return true;
        }

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return reject(response, 401, "未登录或登录已过期");
        }

        List<String> roles = roleMapper.findRoleCodesByUserId(userId);
        if (!roles.contains("ADMIN") && !roles.contains("KEEPER")) {
            return reject(response, 403, "只有管理员和实验员才能执行这个操作");
        }

        return true;
    }

    private boolean reject(HttpServletResponse response, int httpStatus, String message) throws Exception {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + httpStatus + ",\"message\":\"" + message + "\",\"data\":null}");
        return false;
    }
}
