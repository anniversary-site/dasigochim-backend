package com.dasigochim.common.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String uploadLocation;

    public WebConfig(@Value("${app.file.upload-dir}") String uploadDirectory) {
        // 폴더가 아직 없으면 toUri() 가 끝에 '/' 를 붙이지 않아 안의 파일을 찾지 못한다.
        String location = Path.of(uploadDirectory).toAbsolutePath().normalize().toUri().toString();
        this.uploadLocation = location.endsWith("/") ? location : location + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/**").addResourceLocations(uploadLocation);
    }
}
