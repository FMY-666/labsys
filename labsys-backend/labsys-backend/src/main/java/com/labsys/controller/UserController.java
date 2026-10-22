package com.labsys.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labsys.common.Result;
import com.labsys.dto.LoginRequest;
import com.labsys.dto.LoginVO;
import com.labsys.dto.UserVO;
import com.labsys.entity.User;
import com.labsys.entity.UserToken;
import com.labsys.mapper.RoleMapper;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserMapper userMapper;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UserTokenMapper userTokenMapper;
    private final RoleMapper roleMapper;

    public UserController(UserMapper userMapper, UserTokenMapper userTokenMapper, RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.userTokenMapper = userTokenMapper;
        this.roleMapper = roleMapper;
    }

    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest req) {
        User user = userMapper.findByUsername(req.getUsername());
        if (user == null) {
            return new Result(401, "用户名或密码错误", null);
        } else if (!encoder.matches(req.getPassword(), user.getPassword())) {
            return new Result(401, "用户名或密码错误", null);
        } else if (user.getEnabled() != null && user.getEnabled() == 0) {
            // 密码对了才提示停用，否则等于告诉别人“这个用户名存在但被停了”
            return new Result(403, "账号已停用，请联系管理员", null);
        }
        // 校验通过，签发令牌
        String token = UUID.randomUUID().toString();

        UserToken ut = new UserToken();
        ut.setUserId(user.getId());
        ut.setToken(token);
        ut.setExpiresAt(LocalDateTime.now().plusDays(2));

        userTokenMapper.insert(ut);

        // 登录成功后一并带出这个人的角色与可访问页面，前端不用再单独请求一次
        List<String> roles = roleMapper.findRoleCodesByUserId(user.getId());
        List<String> permissions = roleMapper.findMenuPathsByUserId(user.getId());

        return new Result(200, "ok", new LoginVO(user, token, roles, permissions));
    }

    @GetMapping("/me")
    public Result me(@RequestAttribute("userId") Long userId) {
        User user = userMapper.selectById(userId);
        List<String> roles = roleMapper.findRoleCodesByUserId(userId);
        return new Result(200, "ok", new UserVO(user, roles));
    }

    /**
     * 给下拉框和“人名显示”用的极简列表，只返回 id 和 username。
     * 故意不返回密码、角色等字段：调用方只需要知道“3 号叫 keeper”，多给就是泄露。
     */
    @GetMapping("/users/options")
    public Result options() {
        List<User> list = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .select(User::getId, User::getUsername)
                        // 停用的人不出现在下拉里，避免管理员把设备借给一个已经不能登录的账号
                        .eq(User::getEnabled, 1));
        return new Result(200, "ok", list);
    }
}
