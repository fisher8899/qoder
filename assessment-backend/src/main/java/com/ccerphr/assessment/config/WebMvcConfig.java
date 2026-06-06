package com.ccerphr.assessment.config;

import com.ccerphr.assessment.interceptor.DataScopeInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload.path:./uploads}")
    private String uploadPath;

    private final DataScopeInterceptor dataScopeInterceptor;

    public WebMvcConfig(DataScopeInterceptor dataScopeInterceptor) {
        this.dataScopeInterceptor = dataScopeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(dataScopeInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**");
    }

//    @Override
//    public void addResourceHandlers(ResourceHandlerRegistry registry) {
//        String absolutePath = Paths.get(uploadPath).toAbsolutePath().toUri().toString();
//        registry.addResourceHandler("/uploads/**")
//                .addResourceLocations(absolutePath.endsWith("/") ? absolutePath : absolutePath + "/");
//    }

}
