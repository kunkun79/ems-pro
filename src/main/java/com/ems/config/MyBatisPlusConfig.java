package com.ems.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Plus 配置类
 *
 * 【学习要点】
 * @Configuration：告诉Spring这是一个配置类（替代旧的XML配置方式）
 * @Bean：方法返回值会被Spring容器管理
 *
 * 这里主要配置两件事：
 * 1. 分页插件（MyBatis Plus的分页比PageHelper更简单）
 * 2. 可以在这里添加更多拦截器（如乐观锁、防全表更新等）
 */
@Configuration
public class MyBatisPlusConfig {

    /**
     * MyBatis Plus 拦截器链
     * 添加分页插件，支持物理分页
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件：默认 H2，切换到 MySQL 时改为 DbType.MYSQL
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        return interceptor;
    }
}
