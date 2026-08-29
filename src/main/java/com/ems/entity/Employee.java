package com.ems.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工实体
 *
 * 【学习要点——Lombok @Data】
 * 这个 @Data 注解会自动生成：
 * - getXxx() / setXxx()（所有字段的getter/setter）
 * - toString()
 * - equals() / hashCode()
 * - 无参构造器
 *
 * 所以你不需要手写那几十行getter/setter了！
 */
@Data
@TableName("employee")
public class Employee {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;
    private Integer deptId;
    private String gender;
    private Integer age;
    private String phone;
    private String email;
    private String address;
    private BigDecimal salary;
    private LocalDate entryDate;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 员工门户登录用户名（唯一） */
    private String username;

    /** 员工门户登录密码（仅写入，JSON序列化时不出现在响应中） */
    @JsonProperty(access = Access.WRITE_ONLY)
    private String password;

    // ========== 以下字段不在数据库表中，用于关联查询 ==========
    /** 关联查询：部门名称（不存数据库，查询时填充） */
    @TableField(exist = false)
    private String deptName;
}
