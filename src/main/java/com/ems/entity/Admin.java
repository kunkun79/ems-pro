package com.ems.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 管理员实体
 *
 * 【学习要点】
 * @TableName：MyBatis Plus注解，指定对应的数据库表名
 * @TableId(type = AUTO)：主键自增
 * @TableField：字段映射（当Java属性名与数据库列名不一致时使用）
 *
 * 这里Java属性名用驼峰（createTime），数据库是下划线（create_time），
 * 我们在application.yml中配置了 map-underscore-to-camel-case: true，自动映射。
 */
@Data
@TableName("admin")
public class Admin {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String username;
    @JsonProperty(access = Access.WRITE_ONLY)
    private String password;
    private String role;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
