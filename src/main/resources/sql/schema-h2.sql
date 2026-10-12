-- ==========================================
-- 企业管理系统 Pro — H2 建表脚本
-- 启动时自动执行，无需手动导入
-- ==========================================

-- 管理员表
CREATE TABLE IF NOT EXISTS admin (
    id       INT          AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50)  NOT NULL COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码',
    role     VARCHAR(20)  DEFAULT 'ADMIN' COMMENT '角色',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
);

-- 部门表
CREATE TABLE IF NOT EXISTS department (
    id       INT          AUTO_INCREMENT PRIMARY KEY,
    name     VARCHAR(100) NOT NULL COMMENT '部门名称',
    location VARCHAR(200) COMMENT '办公地点',
    head_count INT        DEFAULT 0 COMMENT '人数',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 员工表
CREATE TABLE IF NOT EXISTS employee (
    id            INT          AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(50)  NOT NULL COMMENT '姓名',
    dept_id       INT          COMMENT '部门ID',
    gender        VARCHAR(4)   COMMENT '性别',
    age           INT          COMMENT '年龄',
    phone         VARCHAR(20)  COMMENT '电话',
    email         VARCHAR(100) COMMENT '邮箱',
    address       VARCHAR(200) COMMENT '地址',
    salary        DECIMAL(10,2) DEFAULT 0 COMMENT '薪资',
    entry_date    DATE         COMMENT '入职日期',
    status        VARCHAR(10)  DEFAULT '在职' COMMENT '状态：在职/离职',
    username      VARCHAR(50)  COMMENT '员工门户用户名',
    password      VARCHAR(100) COMMENT '员工门户密码',
    create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (dept_id) REFERENCES department(id)
);

-- 操作日志表（大数据分析用）
CREATE TABLE IF NOT EXISTS operation_log (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT          COMMENT '操作用户ID',
    action      VARCHAR(50)  NOT NULL COMMENT '操作类型：新增/修改/删除/查询',
    target      VARCHAR(50)  COMMENT '操作目标：员工/部门/管理员',
    target_id   INT          COMMENT '目标ID',
    detail      VARCHAR(500) COMMENT '操作详情',
    ip          VARCHAR(50)  COMMENT 'IP地址',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ========== 员工效能业务事实表（H2 开发库同步，仅供联调） ==========
-- 注意：H2 仅用于开发联调，百万级仿真数据请导入 MySQL

CREATE TABLE IF NOT EXISTS attendance (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    emp_id         INT          NOT NULL COMMENT '员工ID',
    work_date      DATE         NOT NULL COMMENT '工作日期',
    sign_in_time   DATETIME     COMMENT '签到时间',
    sign_out_time  DATETIME     COMMENT '签退时间',
    late_minutes   INT          DEFAULT 0 COMMENT '迟到分钟数',
    overtime_hours DECIMAL(5,2) DEFAULT 0 COMMENT '加班小时数',
    leave_type     VARCHAR(10)  DEFAULT '正常' COMMENT '正常/事假/病假/年假/调休/旷工',
    work_hours     DECIMAL(5,2) DEFAULT 0 COMMENT '实际工时',
    create_time    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
);

CREATE TABLE IF NOT EXISTS task (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    emp_id        INT          NOT NULL COMMENT '员工ID',
    task_code     VARCHAR(40)  NOT NULL COMMENT '工单编号',
    task_type     VARCHAR(20)  COMMENT '工单类型',
    difficulty    INT          DEFAULT 3 COMMENT '难度系数1-5',
    assign_date   DATE         COMMENT '派单日期',
    due_date      DATE         COMMENT '要求完成日期',
    finish_date   DATE         COMMENT '实际完成日期',
    status        VARCHAR(10)  DEFAULT '进行中' COMMENT '进行中/已完成/已延期/已取消',
    quality_score DECIMAL(6,2) DEFAULT 0 COMMENT '质量评分0-10',
    create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
);

CREATE TABLE IF NOT EXISTS performance (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    emp_id     INT          NOT NULL COMMENT '员工ID',
    period     VARCHAR(7)   NOT NULL COMMENT '考核周期 YYYY-MM',
    kpi_score  DECIMAL(5,2) COMMENT 'KPI得分',
    grade      VARCHAR(4)   COMMENT '等级 S/A/B/C/D',
    score_by   INT          COMMENT '评分人',
    create_time TIMESTAMP   DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE (emp_id, period)
);

-- ========== MapReduce 作业执行历史（大屏「计算引擎可观测」数据源） ==========
CREATE TABLE IF NOT EXISTS mr_job_history (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_name               VARCHAR(60)  NOT NULL COMMENT '作业名称',
    job_id                 VARCHAR(60)  COMMENT 'Hadoop JobID',
    join_mode              VARCHAR(20)  COMMENT 'map-side / reduce-side',
    framework              VARCHAR(20)  COMMENT 'local / yarn',
    success                INT DEFAULT 0 COMMENT '1成功 0失败',
    duration_ms            BIGINT DEFAULT 0 COMMENT '耗时毫秒',
    map_input_records      BIGINT DEFAULT 0 COMMENT 'Map输入记录数',
    map_output_records     BIGINT DEFAULT 0 COMMENT 'Map输出记录数',
    map_output_bytes       BIGINT DEFAULT 0 COMMENT 'Map输出字节数(Shuffle规模)',
    combine_input_records  BIGINT DEFAULT 0 COMMENT 'Combiner输入',
    combine_output_records BIGINT DEFAULT 0 COMMENT 'Combiner输出',
    spilled_records        BIGINT DEFAULT 0 COMMENT '溢写记录数',
    reduce_input_groups    BIGINT DEFAULT 0 COMMENT 'Reduce输入分组数',
    reduce_input_records   BIGINT DEFAULT 0 COMMENT 'Reduce输入记录数',
    reduce_output_records  BIGINT DEFAULT 0 COMMENT 'Reduce输出记录数',
    launched_maps          INT DEFAULT 0 COMMENT '启动的Map任务数',
    launched_reduces       INT DEFAULT 0 COMMENT '启动的Reduce任务数',
    dqc_total              BIGINT DEFAULT 0 COMMENT 'DQC校验总数',
    dqc_abnormal           BIGINT DEFAULT 0 COMMENT 'DQC异常数',
    error_msg              VARCHAR(500) COMMENT '失败原因',
    output_path            VARCHAR(255) COMMENT '输出路径',
    create_time            TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间'
);
