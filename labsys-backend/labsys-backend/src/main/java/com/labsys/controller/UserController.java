package com.labsys.controller;

import com.labsys.common.Result;
import com.labsys.dto.LoginRequest;
import com.labsys.dto.LoginVO;
import com.labsys.dto.UserVO;
import com.labsys.entity.User;
import com.labsys.entity.UserToken;
import com.labsys.mapper.UserMapper;
import com.labsys.mapper.UserTokenMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserMapper userMapper;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UserTokenMapper userTokenMapper;

    public UserController(UserMapper userMapper, UserTokenMapper userTokenMapper) {
        this.userMapper = userMapper;
        this.userTokenMapper = userTokenMapper;
    }

    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest req) {
        User user = userMapper.findByUsername(req.getUsername());
        if (user == null) {
            return new Result(401, "用户名或密码错误", null);
        } else if (!encoder.matches(req.getPassword(), user.getPassword())) {
            return new Result(401, "用户名或密码错误", null);
        }
        // 校验通过，签发令牌
        String token = UUID.randomUUID().toString();

        UserToken ut = new UserToken();
        ut.setUserId(user.getId());
        ut.setToken(token);
        ut.setExpiresAt(LocalDateTime.now().plusDays(2));

        userTokenMapper.insert(ut);

        return new Result(200, "ok", new LoginVO(user, token));
    }

    @GetMapping("/me")
    public Result me(@RequestAttribute("userId") Long userId) {
        User user = userMapper.selectById(userId);
        return new Result(200, "ok", new UserVO(user));
    }
}
