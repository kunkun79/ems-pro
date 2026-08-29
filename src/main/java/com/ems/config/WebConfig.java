package com.ems.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 *
 * 【学习要点——CORS跨域】
 * 当前后端部署在不同端口/域名时，浏览器会阻止请求。
 * 这个配置让后端允许来自任何前端的请求。
 *
 * 【注意】中文编码通过 application.yml 中的 server.servlet.encoding.force=true 解决，
 * 不需要在代码里手动配消息转换器。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")      // 所有 /api/ 开头的接口
                .allowedOrigins("*")         // 允许所有来源（生产环境要限制！）
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);               // 预检请求缓存1小时
    }
}
