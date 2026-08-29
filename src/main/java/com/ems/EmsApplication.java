package com.ems;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 企业管理系统 Pro — 启动类
 *
 * 【学习要点】
 * 
 * @SpringBootApplication = @Configuration + @EnableAutoConfiguration
 *                        + @ComponentScan
 *                        这是Spring Boot的核心注解，告诉框架：从这里开始扫描并自动配置一切
 *
 * @MapperScan 告诉MyBatis Plus去哪里找Mapper接口
 */
@SpringBootApplication
@MapperScan("com.ems.mapper")
@EnableScheduling
public class EmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmsApplication.class, args);
        System.out.println("========================================");
        System.out.println("  企业管理系统 Pro 启动成功！");
        System.out.println("  访问地址：http://localhost:9090");
        System.out.println("  H2控制台：http://localhost:9090/h2-console");
        System.out.println("  API文档：http://localhost:9090/api");
        System.out.println("========================================");
    }
}
