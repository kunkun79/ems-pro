-- ==========================================
-- DWS → ADS 排名与画像
--
-- 处理内容：
--   1. 员工效能排行榜：部门内排名、全公司排名、环比变化
--   2. 部门效能画像：均值、分档人数、部门第一名、部门排名
--
-- 用到的窗口函数：
--   ROW_NUMBER() 部门内不并列排名
--   RANK()       全公司并列排名
--   LAG()        取上月数值用于计算环比
-- ==========================================

USE ems_dw;

-- ========== 1. 员工效能排行榜 ==========
INSERT OVERWRITE TABLE ads_emp_efficiency_rank PARTITION (dt = '${dt}')
SELECT
    t.*,
    ROUND(t.efficiency_index - t.last_month_index, 2) AS mom_delta
FROM (
    SELECT
        e.emp_id,
        NVL(d.name, CONCAT('EMP_', CAST(e.emp_id AS STRING)))  AS emp_name,
        e.dept_id,
        e.dept_name,
        e.work_month,
        e.efficiency_index,
        e.efficiency_level,
        ROW_NUMBER() OVER (PARTITION BY e.dept_id, e.work_month
                           ORDER BY e.efficiency_index DESC)   AS dept_rank,
        RANK()       OVER (PARTITION BY e.work_month
                           ORDER BY e.efficiency_index DESC)   AS global_rank,
        LAG(e.efficiency_index, 1) OVER (PARTITION BY e.emp_id
                                         ORDER BY e.work_month) AS last_month_index
    FROM dws_emp_efficiency_month e
    LEFT JOIN dwd_dim_employee d
           ON e.emp_id = d.emp_id AND d.end_date = '9999-12-31'
    WHERE e.dt = '${dt}'
) t;

-- ========== 2. 部门效能画像 ==========
-- 内层先按 部门×月份 聚合出基础指标与部门第一名的员工，
-- 外层再用 RANK() 计算部门之间的排名
INSERT OVERWRITE TABLE ads_dept_efficiency_profile PARTITION (dt = '${dt}')
SELECT
    x.*,
    RANK() OVER (PARTITION BY x.work_month ORDER BY x.avg_efficiency DESC) AS dept_rank
FROM (
    SELECT
        g.dept_id,
        g.dept_name,
        g.work_month,
        COUNT(1)                                                       AS emp_cnt,
        ROUND(AVG(g.efficiency_index), 2)                              AS avg_efficiency,
        ROUND(AVG(g.attendance_rate), 4)                               AS avg_attendance,
        ROUND(AVG(g.task_complete_rate), 4)                            AS avg_complete_rate,
        SUM(CASE WHEN g.efficiency_level = '优秀'   THEN 1 ELSE 0 END) AS excellent_cnt,
        SUM(CASE WHEN g.efficiency_level = '待改进' THEN 1 ELSE 0 END) AS improve_cnt,
        MAX(CASE WHEN g.dept_rn = 1 THEN g.emp_id END)                 AS top_emp_id,
        MAX(CASE WHEN g.dept_rn = 1 THEN g.emp_name END)               AS top_emp_name,
        MAX(CASE WHEN g.dept_rn = 1 THEN g.efficiency_index END)       AS top_efficiency
    FROM (
        SELECT
            e.emp_id,
            NVL(d.name, CONCAT('EMP_', CAST(e.emp_id AS STRING))) AS emp_name,
            e.dept_id,
            e.dept_name,
            e.work_month,
            e.efficiency_index,
            e.efficiency_level,
            e.attendance_rate,
            e.task_complete_rate,
            ROW_NUMBER() OVER (PARTITION BY e.dept_id, e.work_month
                               ORDER BY e.efficiency_index DESC) AS dept_rn
        FROM dws_emp_efficiency_month e
        LEFT JOIN dwd_dim_employee d
               ON e.emp_id = d.emp_id AND d.end_date = '9999-12-31'
        WHERE e.dt = '${dt}'
    ) g
    GROUP BY g.dept_id, g.dept_name, g.work_month
) x;
