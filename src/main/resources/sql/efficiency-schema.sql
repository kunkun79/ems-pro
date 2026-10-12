-- ==========================================
-- EMS-Pro 员工效能业务事实表（独立导入版）
-- 用途：单独导入 MySQL，无需重跑 schema-mysql.sql
-- 导入：mysql -u root -p123456 ems_pro < efficiency-schema.sql
-- ==========================================

USE ems_pro;

-- ========== 考勤事实表（效能的「投入」侧数据） ==========
CREATE TABLE IF NOT EXISTS attendance (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    emp_id         INT           NOT NULL COMMENT '员工ID',
    work_date      DATE          NOT NULL COMMENT '工作日期',
    sign_in_time   DATETIME      COMMENT '签到时间',
    sign_out_time  DATETIME      COMMENT '签退时间',
    late_minutes   INT           DEFAULT 0 COMMENT '迟到分钟数',
    overtime_hours DECIMAL(5,2)  DEFAULT 0 COMMENT '加班小时数',
    leave_type     VARCHAR(10)   DEFAULT '正常' COMMENT '正常/事假/病假/年假/调休/旷工',
    work_hours     DECIMAL(5,2)  DEFAULT 0 COMMENT '实际工时',
    create_time    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_emp (emp_id),
    INDEX idx_date (work_date),
    INDEX idx_leave (leave_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤事实表';

-- ========== 任务工单表（效能的「产出」侧数据） ==========
CREATE TABLE IF NOT EXISTS task (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    emp_id        INT          NOT NULL COMMENT '员工ID',
    task_code     VARCHAR(40)  NOT NULL COMMENT '工单编号',
    task_type     VARCHAR(20)  COMMENT '需求开发/缺陷修复/系统运维/测试验证/方案设计/数据报表/客户支持',
    difficulty    INT          DEFAULT 3 COMMENT '难度系数1-5',
    assign_date   DATE         COMMENT '派单日期',
    due_date      DATE         COMMENT '要求完成日期',
    finish_date   DATE         COMMENT '实际完成日期',
    status        VARCHAR(10)  DEFAULT '进行中' COMMENT '进行中/已完成/已延期/已取消',
    quality_score DECIMAL(6,2) DEFAULT 0 COMMENT '质量评分0-10',
    create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_emp (emp_id),
    INDEX idx_status (status),
    INDEX idx_assign (assign_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务工单表';

-- ========== 绩效考核表（效能的「结果」侧数据） ==========
CREATE TABLE IF NOT EXISTS performance (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    emp_id     INT          NOT NULL COMMENT '员工ID',
    period     VARCHAR(7)   NOT NULL COMMENT '考核周期 YYYY-MM',
    kpi_score  DECIMAL(5,2) COMMENT 'KPI得分',
    grade      VARCHAR(4)   COMMENT '等级 S/A/B/C/D',
    score_by   INT          COMMENT '评分人（管理员ID）',
    create_time TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_emp_period (emp_id, period),
    INDEX idx_period (period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='绩效考核表';

-- ========== 可选：结果表容量核对视图（导入数据后可用） ==========
-- SELECT COUNT(*) FROM attendance;
-- SELECT COUNT(*) FROM task;
-- SELECT COUNT(*) FROM performance;
