package com.labsys.config;

import com.labsys.interceptor.AdminInterceptor;
import com.labsys.interceptor.AdminOnlyInterceptor;
import com.labsys.interceptor.AuthInterceptor;
import com.labsys.interceptor.OperLogInterceptor;
import com.labsys.interceptor.StaffInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AdminInterceptor adminInterceptor;
    private final AdminOnlyInterceptor adminOnlyInterceptor;
    private final StaffInterceptor staffInterceptor;
    private final OperLogInterceptor operLogInterceptor;

    public WebConfig(AuthInterceptor authInterceptor,
                     AdminInterceptor adminInterceptor,
                     AdminOnlyInterceptor adminOnlyInterceptor,
                     StaffInterceptor staffInterceptor,
                     OperLogInterceptor operLogInterceptor) {
        this.authInterceptor = authInterceptor;
        this.adminInterceptor = adminInterceptor;
        this.adminOnlyInterceptor = adminOnlyInterceptor;
        this.staffInterceptor = staffInterceptor;
        this.operLogInterceptor = operLogInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录校验：除登录接口外全部拦截，后续拦截器都依赖它写入的 userId
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/login");

        // 角色校验：设备接口只限制写操作
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/devices/**");

        // 操作日志、解禁、用户管理、角色授权、全部菜单，都只给管理员。
        // 注意这里用精确路径而不是 /api/users/** —— /api/users/options 是实验员也要用的下拉接口，
        // 被 /api/users/** 一起挡掉的话，预约管理页的“预约人”就全变成 id 了。
        registry.addInterceptor(adminOnlyInterceptor)
                .addPathPatterns("/api/logs/**", "/api/credit/*/reset",
                        "/api/users", "/api/users/*/password",
                        "/api/users/*/roles", "/api/users/*/enabled",
                        "/api/roles", "/api/roles/*/menus",
                        "/api/menus/all");

        // 员工校验：看全部预约/工单、改状态、看板统计、看违规名单只给管理员和实验员。
        // 提交预约、提交报修、看“我的”、看自己的信用都不在这些路径里，学生照样能用。
        registry.addInterceptor(staffInterceptor)
                .addPathPatterns("/api/reservations", "/api/reservations/*/status",
                        "/api/tickets", "/api/tickets/*/status",
                        "/api/users/options",
                        "/api/credit/users",
                        "/api/stats/**");

        // 操作日志记录：只记写操作，放在最后
        registry.addInterceptor(operLogInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/login");
    }
}
