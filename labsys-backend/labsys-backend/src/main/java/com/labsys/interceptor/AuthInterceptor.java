package com.labsys.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labsys.entity.User;
import com.labsys.entity.UserToken;
import com.labsys.mapper.UserMapper;
import com.labsys.mapper.UserTokenMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final UserTokenMapper userTokenMapper;
    private final UserMapper userMapper;

    public AuthInterceptor(UserTokenMapper userTokenMapper, UserMapper userMapper) {
        this.userTokenMapper = userTokenMapper;
        this.userMapper = userMapper;
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

        // 账号被停用后，已经发出去的令牌也要立刻失效。
        // 只在登录时拦是不够的：票有效期两天，停用一个人之后他还能用一天多。
        // 只查 id 和 enabled：avatar 是 mediumtext，每个请求都顺手捞一遍没必要。
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .select(User::getId, User::getEnabled)
                .eq(User::getId, ut.getUserId()));
        if (user == null || (user.getEnabled() != null && user.getEnabled() == 0)) {
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
