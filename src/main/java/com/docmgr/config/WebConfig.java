package com.docmgr.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve built React frontend
        registry.addResourceHandler("/**")
                .addResourceLocations("file:../client/dist/", "classpath:/static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // SPA fallback: forward all non-API paths to index.html
        registry.addViewController("/").setViewName("forward:/index.html");
        // SPA fallback for share links
        registry.addViewController("/share/{path:[^.]*}").setViewName("forward:/index.html");
    }
}