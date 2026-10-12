package com.ems.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 任务工单实体
 *
 * 对应 MySQL 的 task 表，是员工效能「产出侧」的数据源。
 * ODS 层 ods_task 由此表抽取而来。
 */
@Data
@TableName("task")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer empId;
    private String taskCode;
    private String taskType;
    private Integer difficulty;
    private LocalDate assignDate;
    private LocalDate dueDate;
    private LocalDate finishDate;
    private String status;
    private BigDecimal qualityScore;
    private LocalDateTime createTime;
}
