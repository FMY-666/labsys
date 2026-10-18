package com.labsys.config;

import com.labsys.interceptor.AdminInterceptor;
import com.labsys.interceptor.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AdminInterceptor adminInterceptor;

    public WebConfig(AuthInterceptor authInterceptor, AdminInterceptor adminInterceptor) {
        this.authInterceptor = authInterceptor;
        this.adminInterceptor = adminInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录校验：除登录接口外全部拦截
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/login");

        // 角色校验：只作用于设备接口，需在登录校验之后注册
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/devices/**");
    }
}
