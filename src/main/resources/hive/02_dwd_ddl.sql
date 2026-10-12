-- ==========================================
-- DWD 明细层建表（Data Warehouse Detail）
--
-- 定位：完成清洗、脱敏、维度退化后的明细数据
-- 设计要点：
--   1. ORC 列式存储 + Snappy 压缩，相比 TEXTFILE 显著减少 IO
--   2. 维度退化：把 dept_name 冗余进事实表，减少后续 Join 次数
--   3. 增加 is_valid 质量标记位，支撑 DQC 数据质量校验
--   4. 员工维度采用拉链表，记录历史变更（SCD Type 2）
-- ==========================================

USE ems_dw;

-- ========== 员工维度表（拉链表 SCD Type 2） ==========
-- start_date / end_date 表示该版本记录的生效区间
-- end_date = '9999-12-31' 表示当前有效版本
CREATE TABLE IF NOT EXISTS dwd_dim_employee (
    emp_id          INT     COMMENT '员工ID',
    name            STRING  COMMENT '姓名',
    gender          STRING  COMMENT '性别',
    age             INT     COMMENT '年龄',
    dept_id         INT     COMMENT '部门ID',
    dept_name       STRING  COMMENT '部门名称（维度退化）',
    job_level       STRING  COMMENT '职级 P4-P8',
    seniority_level STRING  COMMENT '司龄分层',
    salary          DECIMAL(10,2) COMMENT '薪资',
    entry_date      STRING  COMMENT '入职日期',
    status          STRING  COMMENT '在职状态',
    start_date      STRING  COMMENT '版本生效日期',
    end_date        STRING  COMMENT '版本失效日期，9999-12-31 为当前有效'
)
COMMENT 'DWD 员工维度拉链表'
STORED AS ORC;

-- ========== 考勤日明细 ==========
CREATE TABLE IF NOT EXISTS dwd_fact_attendance_day (
    emp_id          INT     COMMENT '员工ID',
    dept_id         INT     COMMENT '部门ID',
    dept_name       STRING  COMMENT '部门名称',
    work_date       STRING  COMMENT '工作日期',
    work_month      STRING  COMMENT '所属月份 yyyy-MM',
    is_late         INT     COMMENT '是否迟到 1/0',
    is_leave        INT     COMMENT '是否请假 1/0',
    is_absent       INT     COMMENT '是否旷工 1/0',
    late_minutes    INT     COMMENT '迟到分钟数',
    work_hours      DECIMAL(5,2) COMMENT '实际工时',
    overtime_hours  DECIMAL(5,2) COMMENT '加班小时数',
    is_valid        INT     COMMENT '质量标记：1有效 0异常'
)
COMMENT 'DWD 考勤日明细'
PARTITIONED BY (dt STRING)
STORED AS ORC;

-- ========== 任务工单明细 ==========
CREATE TABLE IF NOT EXISTS dwd_fact_task_detail (
    emp_id          INT     COMMENT '员工ID',
    dept_id         INT     COMMENT '部门ID',
    dept_name       STRING  COMMENT '部门名称',
    task_code       STRING  COMMENT '工单编号',
    task_type       STRING  COMMENT '工单类型',
    difficulty      INT     COMMENT '难度系数1-5',
    assign_date     STRING  COMMENT '派单日期',
    due_date        STRING  COMMENT '要求完成日期',
    finish_date     STRING  COMMENT '实际完成日期',
    assign_month    STRING  COMMENT '派单月份 yyyy-MM',
    is_done         INT     COMMENT '是否完成 1/0',
    is_delayed      INT     COMMENT '是否延期 1/0',
    is_ontime       INT     COMMENT '是否准时 1/0',
    quality_score   DECIMAL(6,2) COMMENT '质量评分',
    is_valid        INT     COMMENT '质量标记：1有效 0异常'
)
COMMENT 'DWD 任务工单明细'
PARTITIONED BY (dt STRING)
STORED AS ORC;

-- ========== 绩效月度明细 ==========
CREATE TABLE IF NOT EXISTS dwd_fact_performance_month (
    emp_id      INT     COMMENT '员工ID',
    dept_id     INT     COMMENT '部门ID',
    dept_name   STRING  COMMENT '部门名称',
    period      STRING  COMMENT '考核周期 yyyy-MM',
    kpi_score   DECIMAL(5,2) COMMENT 'KPI得分',
    grade       STRING  COMMENT '等级 S/A/B/C/D',
    is_valid    INT     COMMENT '质量标记：1有效 0异常'
)
COMMENT 'DWD 绩效月度明细'
PARTITIONED BY (dt STRING)
STORED AS ORC;
