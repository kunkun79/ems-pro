package com.ems.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考勤事实表实体
 *
 * 对应 MySQL 的 attendance 表，是员工效能「投入侧」的数据源。
 * ODS 层 ods_attendance 由此表抽取而来。
 */
@Data
@TableName("attendance")
public class Attendance {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer empId;
    private LocalDate workDate;
    private LocalDateTime signInTime;
    private LocalDateTime signOutTime;
    private Integer lateMinutes;
    private BigDecimal overtimeHours;
    private String leaveType;
    private BigDecimal workHours;
    private LocalDateTime createTime;
}
