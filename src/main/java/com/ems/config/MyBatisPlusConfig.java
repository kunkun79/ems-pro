package com.ems.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Plus 配置类
 *
 * 【学习要点】
 * @Configuration：告诉Spring这是一个配置类（替代旧的XML配置方式）
 * @Bean：方法返回值会被Spring容器管理
 * @Value：读取 application.yml 中的配置项
 *
 * 这里主要配置两件事：
 * 1. 分页插件（MyBatis Plus的分页比PageHelper更简单）
 * 2. 可以在这里添加更多拦截器（如乐观锁、防全表更新等）
 */
@Configuration
public class MyBatisPlusConfig {

    /**
     * 数据源驱动类名，用于决定分页方言
     *
     * dev 环境为 org.h2.Driver，prod 环境为 com.mysql.cj.jdbc.Driver。
     * 这里按驱动判断而不是按 profile 名称判断，是因为驱动类名与实际数据库
     * 强绑定，而 profile 名称可以被随意修改，前者不会说谎。
     */
    @Value("${spring.datasource.driver-class-name:}")
    private String driverClassName;

    /**
     * MyBatis Plus 拦截器链
     * 添加分页插件，支持物理分页
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件：方言按当前数据源驱动自动选择
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(resolveDbType()));
        return interceptor;
    }

    /**
     * 根据数据源驱动推断数据库类型
     *
     * 分页方言决定了最终拼接到 SQL 末尾的 LIMIT 子句写法：
     *   MySQL  → LIMIT ?,?
     *   H2     → 无专用方言，MyBatis Plus 会兜底为 LIMIT ? OFFSET ?
     *
     * 未识别到已知驱动时返回 null，此时 MyBatis Plus 会从 JDBC 连接
     * 元数据自动推断数据库类型，避免在陌生环境下直接报错。
     */
    private DbType resolveDbType() {
        if (driverClassName == null || driverClassName.isEmpty()) {
            return null;
        }
        String driver = driverClassName.toLowerCase();
        if (driver.contains("mysql") || driver.contains("mariadb")) {
            return DbType.MYSQL;
        }
        if (driver.contains("h2")) {
            return DbType.H2;
        }
        if (driver.contains("postgresql")) {
            return DbType.POSTGRE_SQL;
        }
        if (driver.contains("oracle")) {
            return DbType.ORACLE;
        }
        if (driver.contains("sqlserver")) {
            return DbType.SQL_SERVER;
        }
        return null;
    }
}
