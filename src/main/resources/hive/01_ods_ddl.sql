-- ==========================================
-- ODS 贴源层建表（Operation Data Store）
--
-- 定位：与业务库保持一致的原始镜像，不做任何业务加工
-- 设计要点：
--   1. 使用 EXTERNAL TABLE，DROP TABLE 不会误删 HDFS 数据
--   2. 按 dt 分区，支持按天增量重跑与历史追溯
--   3. 保留 TEXTFILE 原始格式，便于排错与数据回溯
-- ==========================================

USE ems_dw;

-- ========== 员工快照 ==========
CREATE EXTERNAL TABLE IF NOT EXISTS ods_employee (
    emp_id      INT     COMMENT '员工ID',
    name        STRING  COMMENT '姓名',
    gender      STRING  COMMENT '性别',
    age         INT     COMMENT '年龄',
    dept_id     INT     COMMENT '部门ID',
    salary      DECIMAL(10,2) COMMENT '薪资',
    entry_date  STRING  COMMENT '入职日期',
    status      STRING  COMMENT '在职状态'
)
COMMENT 'ODS 员工快照表'
PARTITIONED BY (dt STRING COMMENT '数据日期 yyyy-MM-dd')
ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t'
STORED AS TEXTFILE
LOCATION '/user/hive/warehouse/ems_dw/ods/employee';

-- ========== 部门 ==========
CREATE EXTERNAL TABLE IF NOT EXISTS ods_department (
    dept_id     INT     COMMENT '部门ID',
    dept_name   STRING  COMMENT '部门名称',
    location    STRING  COMMENT '办公地点',
    head_count  INT     COMMENT '编制人数'
)
COMMENT 'ODS 部门表'
PARTITIONED BY (dt STRING)
ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t'
STORED AS TEXTFILE
LOCATION '/user/hive/warehouse/ems_dw/ods/department';

-- ========== 考勤事实（一年 11.7 万条，数据量主体） ==========
CREATE EXTERNAL TABLE IF NOT EXISTS ods_attendance (
    emp_id          INT     COMMENT '员工ID',
    work_date       STRING  COMMENT '工作日期',
    sign_in_time    STRING  COMMENT '签到时间',
    sign_out_time   STRING  COMMENT '签退时间',
    late_minutes    INT     COMMENT '迟到分钟数',
    overtime_hours  DECIMAL(5,2) COMMENT '加班小时数',
    leave_type      STRING  COMMENT '正常/事假/病假/年假/调休/旷工',
    work_hours      DECIMAL(5,2) COMMENT '实际工时'
)
COMMENT 'ODS 考勤事实表'
PARTITIONED BY (dt STRING)
ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t'
STORED AS TEXTFILE
LOCATION '/user/hive/warehouse/ems_dw/ods/attendance';

-- ========== 任务工单事实 ==========
CREATE EXTERNAL TABLE IF NOT EXISTS ods_task (
    emp_id          INT     COMMENT '员工ID',
    task_code       STRING  COMMENT '工单编号',
    task_type       STRING  COMMENT '工单类型',
    difficulty      INT     COMMENT '难度系数1-5',
    assign_date     STRING  COMMENT '派单日期',
    due_date        STRING  COMMENT '要求完成日期',
    finish_date     STRING  COMMENT '实际完成日期',
    status          STRING  COMMENT '进行中/已完成/已延期/已取消',
    quality_score   DECIMAL(6,2) COMMENT '质量评分'
)
COMMENT 'ODS 任务工单表'
PARTITIONED BY (dt STRING)
ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t'
STORED AS TEXTFILE
LOCATION '/user/hive/warehouse/ems_dw/ods/task';

-- ========== 绩效考核事实 ==========
CREATE EXTERNAL TABLE IF NOT EXISTS ods_performance (
    emp_id      INT     COMMENT '员工ID',
    period      STRING  COMMENT '考核周期 yyyy-MM',
    kpi_score   DECIMAL(5,2) COMMENT 'KPI得分',
    grade       STRING  COMMENT '等级 S/A/B/C/D'
)
COMMENT 'ODS 绩效考核表'
PARTITIONED BY (dt STRING)
ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t'
STORED AS TEXTFILE
LOCATION '/user/hive/warehouse/ems_dw/ods/performance';
