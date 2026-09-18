package com.datamap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.beans.factory.annotation.Value;

@SpringBootApplication
@MapperScan("com.datamap.mapper")
public class DataMapApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataMapApplication.class, args);
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Value("${datamap.web-root:}")
            private String webRoot;

            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns("http://localhost:*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
            }

            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                // 后端直接托管前端静态文件：把 dist 映射到 /
                // web-root 由配置项 datamap.web-root 指定，默认空（不启用）
                if (webRoot != null && !webRoot.isEmpty()) {
                    String root = webRoot.endsWith("/") ? webRoot : webRoot + "/";
                    registry.addResourceHandler("/**")
                            .addResourceLocations("file:" + root)
                            .resourceChain(false);
                }
            }
        };
    }
}
