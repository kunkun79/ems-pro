-- ==========================================
-- DWS 汇总层建表（Data Warehouse Summary）
--
-- 定位：面向分析主题的宽表，一个主题一张表
-- 设计要点：
--   1. 员工效能宽表：把考勤/任务/绩效三类事实按 员工×月份 粒度打平
--   2. 部门效能表：用 GROUPING SETS 一次扫描产出多种维度组合
--   3. 效能指数采用固定权重模型，口径统一可复现
-- ==========================================

USE ems_dw;

-- ========== 员工月度效能宽表 ==========
CREATE TABLE IF NOT EXISTS dws_emp_efficiency_month (
    emp_id              INT     COMMENT '员工ID',
    dept_id             INT     COMMENT '部门ID',
    dept_name           STRING  COMMENT '部门名称',
    work_month          STRING  COMMENT '统计月份 yyyy-MM',
    seniority_level     STRING  COMMENT '司龄分层',
    job_level           STRING  COMMENT '职级',

    -- 投入侧指标
    attend_days         INT     COMMENT '应出勤天数',
    actual_days         INT     COMMENT '实际出勤天数',
    late_count          INT     COMMENT '迟到次数',
    leave_count         INT     COMMENT '请假次数',
    absent_count        INT     COMMENT '旷工次数',
    attendance_rate     DECIMAL(6,4) COMMENT '出勤率',
    avg_work_hours      DECIMAL(6,2) COMMENT '日均工时',
    overtime_hours      DECIMAL(8,2) COMMENT '月加班总时长',

    -- 产出侧指标
    task_total          INT     COMMENT '工单总数',
    task_done           INT     COMMENT '完成数',
    task_delayed        INT     COMMENT '延期数',
    task_complete_rate  DECIMAL(6,4) COMMENT '任务完成率',
    ontime_rate         DECIMAL(6,4) COMMENT '准时交付率',
    avg_quality_score   DECIMAL(6,2) COMMENT '平均质量分',

    -- 结果侧指标
    kpi_score           DECIMAL(5,2) COMMENT 'KPI得分',

    -- 综合指标
    efficiency_index    DECIMAL(6,2) COMMENT '效能综合指数 0-100',
    efficiency_level    STRING  COMMENT '优秀/良好/一般/待改进'
)
COMMENT 'DWS 员工月度效能宽表'
PARTITIONED BY (dt STRING COMMENT '数据日期')
STORED AS ORC;

-- ========== 部门月度效能汇总（多粒度） ==========
-- grouping_id 取自 GROUPING__ID，用于区分当前行所属的聚合粒度
-- 判读方式：被聚合掉的维度列值为 NULL，结合 grouping_id 即可确定粒度
--   work_month IS NULL      -> 部门 × 司龄段
--   seniority_level IS NULL -> 部门 × 月份
--   两者都不为空            -> 部门 × 月份 × 司龄段
CREATE TABLE IF NOT EXISTS dws_dept_efficiency_month (
    dept_id             INT     COMMENT '部门ID',
    dept_name           STRING  COMMENT '部门名称',
    work_month          STRING  COMMENT '统计月份',
    seniority_level     STRING  COMMENT '司龄分层',
    grouping_id         INT     COMMENT '聚合粒度标识',
    emp_cnt             INT     COMMENT '员工数',
    avg_efficiency      DECIMAL(6,2) COMMENT '平均效能指数',
    avg_attendance      DECIMAL(6,4) COMMENT '平均出勤率',
    avg_complete_rate   DECIMAL(6,4) COMMENT '平均任务完成率',
    avg_ontime_rate     DECIMAL(6,4) COMMENT '平均准时率',
    total_overtime      DECIMAL(10,2) COMMENT '加班总时长'
)
COMMENT 'DWS 部门效能多粒度汇总'
PARTITIONED BY (dt STRING)
STORED AS ORC;
