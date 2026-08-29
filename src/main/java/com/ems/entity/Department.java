package com.ems.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 部门实体
 */
@Data
@TableName("department")
public class Department {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;
    private String location;
    private Integer headCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
