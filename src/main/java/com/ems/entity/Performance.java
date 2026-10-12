package com.ems.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 绩效考核实体
 *
 * 对应 MySQL 的 performance 表，是员工效能「结果侧」的数据源。
 * ODS 层 ods_performance 由此表抽取而来。
 */
@Data
@TableName("performance")
public class Performance {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer empId;

    /** 考核周期，格式 yyyy-MM */
    private String period;

    private BigDecimal kpiScore;

    /** 等级 S/A/B/C/D */
    private String grade;

    private Integer scoreBy;
    private LocalDateTime createTime;
}
