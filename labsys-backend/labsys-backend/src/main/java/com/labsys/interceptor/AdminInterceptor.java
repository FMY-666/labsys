package com.labsys.interceptor;

import com.labsys.mapper.RoleMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 角色校验：写操作（新增 / 修改 / 删除）要求管理员角色，查询不限。
 * 需要在 AuthInterceptor 之后注册，userId 由后者写入 request。
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    private final RoleMapper roleMapper;

    public AdminInterceptor(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String method = request.getMethod();

        // GET 谁都能看；只有写操作（新增/修改/删除）才要求管理员
        boolean isWrite = "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method);
        if (!isWrite) {
            return true;
        }

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return reject(response, 401, "未登录或登录已过期");
        }

        // 角色存在 sys_user_role 里，一个人可能同时是实验员和管理员
        List<String> roles = roleMapper.findRoleCodesByUserId(userId);
        if (!roles.contains("ADMIN")) {
            return reject(response, 403, "只有管理员才能执行这个操作");
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
