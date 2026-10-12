-- ==========================================
-- ODS → DWD 清洗转换
--
-- 处理内容：
--   1. 维度退化：把 dept_name 冗余进事实表
--   2. 数据清洗：空值兜底、异常值标记（is_valid）
--   3. 维度加工：由薪资推导职级，由入职日期推导司龄分层
--   4. 缓慢变化维：员工维度表按拉链方式记录历史版本
-- ==========================================

USE ems_dw;

-- ========== 1. 考勤日明细 ==========
INSERT OVERWRITE TABLE dwd_fact_attendance_day PARTITION (dt = '${dt}')
SELECT
    a.emp_id,
    e.dept_id,
    NVL(d.dept_name, '未知部门')                              AS dept_name,
    a.work_date,
    SUBSTR(a.work_date, 1, 7)                                  AS work_month,
    CASE WHEN a.late_minutes > 0 THEN 1 ELSE 0 END             AS is_late,
    CASE WHEN COALESCE(a.leave_type, '正常') NOT IN ('正常', '旷工') THEN 1 ELSE 0 END AS is_leave,
    CASE WHEN a.leave_type = '旷工' THEN 1 ELSE 0 END           AS is_absent,
    a.late_minutes,
    a.work_hours,
    a.overtime_hours,
    -- DQC 质量标记：工时必须在 0~24 小时之间
    CASE WHEN a.work_hours >= 0 AND a.work_hours <= 24 THEN 1 ELSE 0 END AS is_valid
FROM ods_attendance a
LEFT JOIN ods_employee   e ON a.emp_id = e.emp_id AND e.dt = '${dt}'
LEFT JOIN ods_department d ON e.dept_id = d.dept_id AND d.dt = '${dt}'
WHERE a.dt = '${dt}';

-- ========== 2. 任务工单明细 ==========
INSERT OVERWRITE TABLE dwd_fact_task_detail PARTITION (dt = '${dt}')
SELECT
    t.emp_id,
    e.dept_id,
    NVL(d.dept_name, '未知部门')                              AS dept_name,
    t.task_code,
    t.task_type,
    t.difficulty,
    t.assign_date,
    t.due_date,
    t.finish_date,
    SUBSTR(t.assign_date, 1, 7)                                AS assign_month,
    CASE WHEN t.status = '已完成' THEN 1 ELSE 0 END            AS is_done,
    CASE WHEN t.status = '已延期'
              OR (t.status = '已完成' AND t.finish_date > t.due_date) THEN 1 ELSE 0 END AS is_delayed,
    CASE WHEN t.status = '已完成' AND t.finish_date <= t.due_date THEN 1 ELSE 0 END     AS is_ontime,
    t.quality_score,
    -- DQC 质量标记：质量分必须在 0~10 之间
    CASE WHEN t.quality_score >= 0 AND t.quality_score <= 10 THEN 1 ELSE 0 END AS is_valid
FROM ods_task t
LEFT JOIN ods_employee   e ON t.emp_id = e.emp_id AND e.dt = '${dt}'
LEFT JOIN ods_department d ON e.dept_id = d.dept_id AND d.dt = '${dt}'
WHERE t.dt = '${dt}';

-- ========== 3. 绩效月度明细 ==========
INSERT OVERWRITE TABLE dwd_fact_performance_month PARTITION (dt = '${dt}')
SELECT
    p.emp_id,
    e.dept_id,
    NVL(d.dept_name, '未知部门')                              AS dept_name,
    p.period,
    p.kpi_score,
    p.grade,
    CASE WHEN p.kpi_score >= 0 AND p.kpi_score <= 100 THEN 1 ELSE 0 END AS is_valid
FROM ods_performance p
LEFT JOIN ods_employee   e ON p.emp_id = e.emp_id AND e.dt = '${dt}'
LEFT JOIN ods_department d ON e.dept_id = d.dept_id AND d.dt = '${dt}'
WHERE p.dt = '${dt}';

-- ========== 4. 员工维度拉链表（SCD Type 2） ==========
-- 三部分合并：已失效历史版本 + 未变更当前版本（或关闭变更版本）+ 新增/变更的新版本
INSERT OVERWRITE TABLE dwd_dim_employee
SELECT
    emp_id, name, gender, age, dept_id, dept_name,
    job_level, seniority_level, salary, entry_date, status,
    start_date, end_date
FROM (
    -- 4.1 已失效的历史版本：原样保留
    SELECT
        emp_id, name, gender, age, dept_id, dept_name,
        job_level, seniority_level, salary, entry_date, status,
        start_date, end_date
    FROM dwd_dim_employee
    WHERE end_date <> '9999-12-31'

    UNION ALL

    -- 4.2 当前有效版本：属性未变则保留；属性变化则关闭该版本（end_date = 当日）
    SELECT
        h.emp_id, h.name, h.gender, h.age, h.dept_id, h.dept_name,
        h.job_level, h.seniority_level, h.salary, h.entry_date, h.status,
        h.start_date,
        CASE WHEN n.emp_id IS NOT NULL
                  AND (h.salary <> n.salary OR h.dept_id <> n.dept_id OR h.status <> n.status)
             THEN '${dt}' ELSE h.end_date END AS end_date
    FROM dwd_dim_employee h
    LEFT JOIN ods_employee n ON h.emp_id = n.emp_id AND n.dt = '${dt}'
    WHERE h.end_date = '9999-12-31'

    UNION ALL

    -- 4.3 新增员工 或 属性发生变更的员工：写入新版本
    SELECT
        n.emp_id,
        n.name,
        n.gender,
        n.age,
        n.dept_id,
        NVL(d.dept_name, '未知部门') AS dept_name,
        CASE WHEN n.salary >= 35000 THEN 'P8'
             WHEN n.salary >= 25000 THEN 'P7'
             WHEN n.salary >= 18000 THEN 'P6'
             WHEN n.salary >= 12000 THEN 'P5'
             ELSE 'P4' END AS job_level,
        CASE WHEN FLOOR(DATEDIFF('${dt}', n.entry_date) / 365) < 1  THEN '1年以内'
             WHEN FLOOR(DATEDIFF('${dt}', n.entry_date) / 365) < 3  THEN '1-3年'
             WHEN FLOOR(DATEDIFF('${dt}', n.entry_date) / 365) < 5  THEN '3-5年'
             WHEN FLOOR(DATEDIFF('${dt}', n.entry_date) / 365) < 10 THEN '5-10年'
             ELSE '10年以上' END AS seniority_level,
        n.salary,
        n.entry_date,
        n.status,
        '${dt}'        AS start_date,
        '9999-12-31'   AS end_date
    FROM ods_employee n
    LEFT JOIN ods_department d ON n.dept_id = d.dept_id AND d.dt = '${dt}'
    LEFT JOIN dwd_dim_employee h
           ON n.emp_id = h.emp_id AND h.end_date = '9999-12-31'
    WHERE n.dt = '${dt}'
      AND (h.emp_id IS NULL
           OR h.salary <> n.salary
           OR h.dept_id <> n.dept_id
           OR h.status <> n.status)
) t;
