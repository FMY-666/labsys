package com.labsys.interceptor;

import com.labsys.entity.UserToken;
import com.labsys.mapper.UserTokenMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final UserTokenMapper userTokenMapper;

    public AuthInterceptor(UserTokenMapper userTokenMapper) {
        this.userTokenMapper = userTokenMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // 请求头格式：Authorization: Bearer <token>
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return reject(response);
        }
        String token = header.substring(7);

        // 校验令牌是否存在
        UserToken ut = userTokenMapper.findByToken(token);
        if (ut == null) {
            return reject(response);
        }

        // 校验令牌是否过期
        if (ut.getExpiresAt().isBefore(LocalDateTime.now())) {
            return reject(response);
        }

        // 校验通过，把 userId 传递给后续的 Controller
        request.setAttribute("userId", ut.getUserId());
        return true;
    }

    private boolean reject(HttpServletResponse response) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\",\"data\":null}");
        return false;
    }
}
