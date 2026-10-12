-- ==========================================
-- ADS 应用层建表（Application Data Store）
--
-- 定位：直接面向前端展示的结果表
-- 设计要点：
--   1. 结果数据量小，查询秒级响应，可回写 MySQL 供 ECharts 直连
--   2. 排名、同环比等派生指标在此层计算完成，前端不做计算
-- ==========================================

USE ems_dw;

-- ========== 员工效能排行榜 ==========
CREATE TABLE IF NOT EXISTS ads_emp_efficiency_rank (
    emp_id              INT     COMMENT '员工ID',
    emp_name            STRING  COMMENT '员工姓名',
    dept_id             INT     COMMENT '部门ID',
    dept_name           STRING  COMMENT '部门名称',
    work_month          STRING  COMMENT '统计月份',
    efficiency_index    DECIMAL(6,2) COMMENT '效能综合指数',
    efficiency_level    STRING  COMMENT '效能等级',
    dept_rank           INT     COMMENT '部门内排名',
    global_rank         INT     COMMENT '全公司排名',
    last_month_index    DECIMAL(6,2) COMMENT '上月效能指数',
    mom_delta           DECIMAL(6,2) COMMENT '环比变化值'
)
COMMENT 'ADS 员工效能排行榜'
PARTITIONED BY (dt STRING)
STORED AS ORC;

-- ========== 部门效能画像 ==========
CREATE TABLE IF NOT EXISTS ads_dept_efficiency_profile (
    dept_id             INT     COMMENT '部门ID',
    dept_name           STRING  COMMENT '部门名称',
    work_month          STRING  COMMENT '统计月份',
    emp_cnt             INT     COMMENT '参与统计人数',
    avg_efficiency      DECIMAL(6,2) COMMENT '平均效能指数',
    avg_attendance      DECIMAL(6,4) COMMENT '平均出勤率',
    avg_complete_rate   DECIMAL(6,4) COMMENT '平均完成率',
    excellent_cnt       INT     COMMENT '优秀人数',
    improve_cnt         INT     COMMENT '待改进人数',
    top_emp_id          INT     COMMENT '部门第一名员工ID',
    top_emp_name        STRING  COMMENT '部门第一名姓名',
    top_efficiency      DECIMAL(6,2) COMMENT '部门最高分',
    dept_rank           INT     COMMENT '部门排名'
)
COMMENT 'ADS 部门效能画像'
PARTITIONED BY (dt STRING)
STORED AS ORC;
