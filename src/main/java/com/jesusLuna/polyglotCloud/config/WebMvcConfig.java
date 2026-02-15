package com.jesusLuna.polyglotCloud.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.jesusLuna.polyglotCloud.Interceptor.RateLimitInterceptor;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    
    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/translations/**", "/snippets/**")
                .excludePathPatterns("/translations/*/versions/*/votes/stats"); // Excluir endpoints de solo lectura
    }
}