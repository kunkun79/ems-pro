-- ==========================================
-- 企业管理系统 Pro — MySQL 建库建表脚本
-- 使用方法：在MySQL中执行此脚本即可
--   mysql -u root -p < schema-mysql.sql
-- ==========================================

CREATE DATABASE IF NOT EXISTS ems_pro
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE ems_pro;

-- 管理员表
CREATE TABLE IF NOT EXISTS admin (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL COMMENT '用户名',
    password    VARCHAR(100) NOT NULL COMMENT '密码',
    role        VARCHAR(20)  DEFAULT 'ADMIN' COMMENT '角色',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

-- 部门表
CREATE TABLE IF NOT EXISTS department (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL COMMENT '部门名称',
    location    VARCHAR(200) COMMENT '办公地点',
    head_count  INT DEFAULT 0 COMMENT '人数',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

-- 员工表
CREATE TABLE IF NOT EXISTS employee (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50) NOT NULL COMMENT '姓名',
    dept_id     INT COMMENT '部门ID',
    gender      VARCHAR(4) COMMENT '性别',
    age         INT COMMENT '年龄',
    phone       VARCHAR(20) COMMENT '电话',
    email       VARCHAR(100) COMMENT '邮箱',
    address     VARCHAR(200) COMMENT '地址',
    salary      DECIMAL(10,2) DEFAULT 0 COMMENT '薪资',
    entry_date  DATE COMMENT '入职日期',
    status      VARCHAR(10) DEFAULT '在职' COMMENT '状态',
    username    VARCHAR(50) COMMENT '员工门户用户名',
    password    VARCHAR(100) COMMENT '员工门户密码',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_dept (dept_id),
    INDEX idx_name (name),
    INDEX idx_username (username),
    FOREIGN KEY (dept_id) REFERENCES department(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工表';

-- 操作日志表
CREATE TABLE IF NOT EXISTS operation_log (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT COMMENT '操作用户ID',
    action      VARCHAR(50) NOT NULL COMMENT '操作类型',
    target      VARCHAR(50) COMMENT '操作目标',
    target_id   INT COMMENT '目标ID',
    detail      VARCHAR(500) COMMENT '操作详情',
    ip          VARCHAR(50) COMMENT 'IP地址',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_time (create_time),
    INDEX idx_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- ========== 初始数据（INSERT IGNORE 防止重复导入） ==========
INSERT IGNORE INTO admin (username, password, role) VALUES
('admin', '123456', 'ADMIN'),
('zhangsan', '123456', 'USER');

INSERT INTO department (name, location, head_count) VALUES
('技术部', 'A栋3层', 30), ('市场部', 'A栋5层', 20), ('财务部', 'B栋2层', 10),
('人事部', 'B栋4层', 8), ('运营部', 'C栋1层', 15), ('产品部', 'A栋4层', 12),
('客服部', 'C栋3层', 25);

INSERT INTO employee (name, dept_id, gender, age, phone, email, address, salary, entry_date, status) VALUES
('张三', 1, '男', 28, '13800001001', 'zhangsan@company.com', '北京市朝阳区', 15000.00, '2020-03-15', '在职'),
('李四', 1, '女', 32, '13800001002', 'lisi@company.com', '北京市海淀区', 22000.00, '2018-07-01', '在职'),
('王五', 2, '男', 26, '13800001003', 'wangwu@company.com', '上海市浦东新区', 12000.00, '2021-01-10', '在职'),
('赵六', 2, '女', 35, '13800001004', 'zhaoliu@company.com', '上海市静安区', 25000.00, '2016-05-20', '在职'),
('孙七', 3, '男', 42, '13800001005', 'sunqi@company.com', '广州市天河区', 30000.00, '2014-09-01', '在职'),
('周八', 3, '女', 29, '13800001006', 'zhouba@company.com', '广州市越秀区', 18000.00, '2019-11-15', '在职'),
('吴九', 4, '女', 31, '13800001007', 'wujiu@company.com', '深圳市南山区', 20000.00, '2017-03-10', '在职'),
('郑十', 4, '男', 27, '13800001008', 'zhengshi@company.com', '深圳市福田区', 13000.00, '2022-06-01', '在职'),
('陈十一', 5, '男', 38, '13800001009', 'chen@company.com', '杭州市西湖区', 28000.00, '2015-08-12', '在职'),
('刘十二', 5, '女', 24, '13800001010', 'liu@company.com', '杭州市滨江区', 10000.00, '2023-01-05', '在职'),
('黄十三', 6, '男', 33, '13800001011', 'huang@company.com', '成都市武侯区', 17000.00, '2018-04-18', '在职'),
('林十四', 7, '女', 25, '13800001012', 'lin@company.com', '武汉市洪山区', 9500.00, '2022-09-01', '在职');

-- ========== 员工效能业务事实表（Hive 数仓分层建模的数据源） ==========

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
    create_time            TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    INDEX idx_create (create_time),
    INDEX idx_job (job_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MapReduce 作业执行历史';
