package com.labsys.controller;

import com.labsys.common.Result;
import com.labsys.service.CreditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预约信用。学生的状态由前端在“我的预约”页展示，管理员在“预约管理”页解禁。
 * 路径特意不放在 /reservations 下面：那个前缀有一部分归 StaffInterceptor 管，
 * 混在一起以后再加接口很容易被顺手挡掉。解禁只认 ADMIN，见 WebConfig。
 */
@RestController
@RequestMapping("/api")
public class CreditController {

    private final CreditService creditService;

    public CreditController(CreditService creditService) {
        this.creditService = creditService;
    }

    /** 看自己的信用：分数、违规次数、是否被冻结、还能约多久 */
    @GetMapping("/credit/me")
    public Result me(@RequestAttribute("userId") Long userId) {
        return new Result(200, "ok", creditService.view(creditService.getOrCreate(userId)));
    }

    /** 管理员/实验员看违规名单，只返回有违规或处于封禁状态的人 */
    @GetMapping("/credit/users")
    public Result users() {
        return new Result(200, "ok", creditService.listAbnormal());
    }

    /** 管理员解禁：违规清零、封禁解除 */
    @PostMapping("/credit/{userId}/reset")
    public Result reset(@PathVariable Long userId) {
        creditService.reset(userId);
        return new Result(200, "已解禁", null);
    }
}
