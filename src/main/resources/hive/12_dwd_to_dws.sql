-- ==========================================
-- DWD → DWS 多维聚合
--
-- 处理内容：
--   1. 把考勤、任务、绩效三类事实按「员工 × 月份」粒度打平成一张宽表
--   2. 按权重模型计算效能综合指数，并划分效能等级
--   3. 用 GROUPING SETS 一次扫描产出部门效能的多种维度组合
--
-- 效能指数权重模型（口径统一、可复现）：
--   0.30 × 任务完成率 + 0.25 × KPI得分 + 0.20 × 出勤率
-- + 0.15 × 准时交付率 + 0.10 × 质量评分（各指标统一折算为百分制）
-- ==========================================

USE ems_dw;

-- 分组集合与分区相关参数
SET hive.exec.dynamic.partition = true;
SET hive.exec.dynamic.partition.mode = nonstrict;
SET hive.new.job.grouping.set.cardinality = 64;

-- ========== 1. 员工月度效能宽表 ==========
-- 内层子查询完成指标计算，外层只做等级划分，避免权重公式重复书写
INSERT OVERWRITE TABLE dws_emp_efficiency_month PARTITION (dt = '${dt}')
SELECT
    t.*,
    CASE WHEN t.efficiency_index >= 85 THEN '优秀'
         WHEN t.efficiency_index >= 75 THEN '良好'
         WHEN t.efficiency_index >= 60 THEN '一般'
         ELSE '待改进' END AS efficiency_level
FROM (
    SELECT
        base.emp_id,
        base.dept_id,
        base.dept_name,
        base.work_month,
        NVL(e.seniority_level, '未知')      AS seniority_level,
        NVL(e.job_level, '未知')            AS job_level,

        -- 投入侧指标
        NVL(att.attend_days, 0)             AS attend_days,
        NVL(att.actual_days, 0)             AS actual_days,
        NVL(att.late_count, 0)              AS late_count,
        NVL(att.leave_count, 0)             AS leave_count,
        NVL(att.absent_count, 0)            AS absent_count,
        NVL(att.attendance_rate, 0)         AS attendance_rate,
        NVL(att.avg_work_hours, 0)          AS avg_work_hours,
        NVL(att.overtime_hours, 0)          AS overtime_hours,

        -- 产出侧指标
        NVL(tk.task_total, 0)               AS task_total,
        NVL(tk.task_done, 0)                AS task_done,
        NVL(tk.task_delayed, 0)             AS task_delayed,
        NVL(tk.task_complete_rate, 0)       AS task_complete_rate,
        NVL(tk.ontime_rate, 0)              AS ontime_rate,
        NVL(tk.avg_quality_score, 0)        AS avg_quality_score,

        -- 结果侧指标
        NVL(pf.kpi_score, 0)                AS kpi_score,

        -- 效能综合指数
        ROUND(
              NVL(tk.task_complete_rate, 0) * 100 * 0.30
            + NVL(pf.kpi_score, 0)          * 0.25
            + NVL(att.attendance_rate, 0)   * 100 * 0.20
            + NVL(tk.ontime_rate, 0)        * 100 * 0.15
            + NVL(tk.avg_quality_score, 0)  * 10  * 0.10
        , 2)                                AS efficiency_index

    FROM (
        -- 以「有考勤记录的员工 × 月份」为主骨架，保证不漏人
        SELECT DISTINCT emp_id, dept_id, dept_name, work_month
        FROM dwd_fact_attendance_day
        WHERE dt = '${dt}' AND is_valid = 1
    ) base

    LEFT JOIN (
        SELECT emp_id, work_month,
               COUNT(1)                                                          AS attend_days,
               SUM(CASE WHEN is_absent = 0 AND is_leave = 0 THEN 1 ELSE 0 END)   AS actual_days,
               SUM(is_late)                                                      AS late_count,
               SUM(is_leave)                                                     AS leave_count,
               SUM(is_absent)                                                    AS absent_count,
               ROUND(SUM(CASE WHEN is_absent = 0 AND is_leave = 0 THEN 1 ELSE 0 END) / COUNT(1), 4) AS attendance_rate,
               ROUND(AVG(work_hours), 2)                                         AS avg_work_hours,
               ROUND(SUM(overtime_hours), 2)                                     AS overtime_hours
        FROM dwd_fact_attendance_day
        WHERE dt = '${dt}' AND is_valid = 1
        GROUP BY emp_id, work_month
    ) att ON base.emp_id = att.emp_id AND base.work_month = att.work_month

    LEFT JOIN (
        SELECT emp_id, assign_month AS work_month,
               COUNT(1)                                                     AS task_total,
               SUM(is_done)                                                 AS task_done,
               SUM(is_delayed)                                              AS task_delayed,
               ROUND(SUM(is_done) / COUNT(1), 4)                            AS task_complete_rate,
               CASE WHEN SUM(is_done) = 0 THEN 0
                    ELSE ROUND(SUM(is_ontime) / SUM(is_done), 4) END        AS ontime_rate,
               ROUND(AVG(CASE WHEN is_done = 1 THEN quality_score END), 2)  AS avg_quality_score
        FROM dwd_fact_task_detail
        WHERE dt = '${dt}' AND is_valid = 1
        GROUP BY emp_id, assign_month
    ) tk ON base.emp_id = tk.emp_id AND base.work_month = tk.work_month

    LEFT JOIN (
        SELECT emp_id, period AS work_month, MAX(kpi_score) AS kpi_score
        FROM dwd_fact_performance_month
        WHERE dt = '${dt}' AND is_valid = 1
        GROUP BY emp_id, period
    ) pf ON base.emp_id = pf.emp_id AND base.work_month = pf.work_month

    LEFT JOIN dwd_dim_employee e
           ON base.emp_id = e.emp_id AND e.end_date = '9999-12-31'
) t;

-- ========== 2. 部门效能多粒度汇总（GROUPING SETS） ==========
-- 一次全表扫描同时产出三种粒度，相比三次 UNION ALL 减少约 2/3 的 IO
-- GROUPING__ID 用于区分当前行所属的聚合粒度
INSERT OVERWRITE TABLE dws_dept_efficiency_month PARTITION (dt = '${dt}')
SELECT
    dept_id,
    dept_name,
    work_month,
    seniority_level,
    GROUPING__ID                        AS grouping_id,
    COUNT(DISTINCT emp_id)              AS emp_cnt,
    ROUND(AVG(efficiency_index), 2)     AS avg_efficiency,
    ROUND(AVG(attendance_rate), 4)      AS avg_attendance,
    ROUND(AVG(task_complete_rate), 4)   AS avg_complete_rate,
    ROUND(AVG(ontime_rate), 4)          AS avg_ontime_rate,
    ROUND(SUM(overtime_hours), 2)       AS total_overtime
FROM dws_emp_efficiency_month
WHERE dt = '${dt}'
GROUP BY dept_id, dept_name, work_month, seniority_level
GROUPING SETS (
    (dept_id, dept_name, work_month),                  -- 部门 × 月份
    (dept_id, dept_name, seniority_level),             -- 部门 × 司龄段
    (dept_id, dept_name, work_month, seniority_level)  -- 部门 × 月份 × 司龄段
);
