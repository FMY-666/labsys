package com.labsys.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labsys.common.Result;
import com.labsys.dto.LoginRequest;
import com.labsys.dto.LoginVO;
import com.labsys.dto.PasswordRequest;
import com.labsys.dto.UserVO;
import com.labsys.entity.User;
import com.labsys.entity.UserToken;
import com.labsys.mapper.RoleMapper;
import com.labsys.mapper.UserMapper;
import com.labsys.mapper.UserTokenMapper;
import com.labsys.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final LoginAttemptService loginAttemptService;

    public UserController(UserMapper userMapper, UserTokenMapper userTokenMapper,
                          RoleMapper roleMapper, LoginAttemptService loginAttemptService) {
        this.userMapper = userMapper;
        this.userTokenMapper = userTokenMapper;
        this.roleMapper = roleMapper;
        this.loginAttemptService = loginAttemptService;
    }

    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest req) {

        long locked = loginAttemptService.lockedMinutes(req.getUsername());
        if (locked > 0) {
            return new Result(403, "密码错误次数过多，请 " + locked + " 分钟后再试", null);
        }

        User user = userMapper.findByUsername(req.getUsername());
        if (user == null || !encoder.matches(req.getPassword(), user.getPassword())) {
            // 用户名不存在也记一次失败：否则能从"是否被锁"反推出哪些用户名是存在的
            loginAttemptService.onFailure(req.getUsername());
            return new Result(401, "用户名或密码错误", null);
        } else if (user.getEnabled() != null && user.getEnabled() == 0) {
            // 密码对了才提示停用，否则等于告诉别人“这个用户名存在但被停了”
            return new Result(403, "账号已停用，请联系管理员", null);
        }
        loginAttemptService.onSuccess(req.getUsername());
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
     * 退出登录：把这张票从库里删掉。
     * 只清浏览器的登录态是不够的 —— 票还在有效期内，谁拿到它都能继续用。
     */
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            userTokenMapper.delete(new LambdaQueryWrapper<UserToken>()
                    .eq(UserToken::getToken, header.substring(7)));
        }
        return new Result(200, "已退出登录", null);
    }

    /** 改自己的密码。任何角色都能用，不需要管理员。 */
    @PutMapping("/me/password")
    public Result changePassword(@RequestAttribute("userId") Long userId,
                                 @RequestBody PasswordRequest req) {
        if (req.getOldPassword() == null || req.getNewPassword() == null) {
            return new Result(400, "原密码和新密码都不能为空", null);
        }
        if (req.getNewPassword().length() < 6) {
            return new Result(400, "新密码至少 6 位", null);
        }
        if (req.getNewPassword().equals(req.getOldPassword())) {
            return new Result(400, "新密码不能和原密码相同", null);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return new Result(404, "用户不存在", null);
        }
        if (!encoder.matches(req.getOldPassword(), user.getPassword())) {
            return new Result(400, "原密码不对", null);
        }

        user.setPassword(encoder.encode(req.getNewPassword()));
        userMapper.updateById(user);

        // 改完密码把所有票清掉：别的设备上那些旧登录不该继续有效
        userTokenMapper.delete(new LambdaQueryWrapper<UserToken>().eq(UserToken::getUserId, userId));

        return new Result(200, "密码已修改，请重新登录", null);
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
