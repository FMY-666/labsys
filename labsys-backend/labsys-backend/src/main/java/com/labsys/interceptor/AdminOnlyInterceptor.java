package com.labsys.interceptor;

import com.labsys.mapper.RoleMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 管理员专属接口：整个路径都要求 ADMIN，不分读写。
 * 需要在 AuthInterceptor 之后注册。
 */
@Component
public class AdminOnlyInterceptor implements HandlerInterceptor {

    private final RoleMapper roleMapper;

    public AdminOnlyInterceptor(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return reject(response, 401, "未登录或登录已过期");
        }

        List<String> roles = roleMapper.findRoleCodesByUserId(userId);
        if (!roles.contains("ADMIN")) {
            return reject(response, 403, "只有管理员才能访问");
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
