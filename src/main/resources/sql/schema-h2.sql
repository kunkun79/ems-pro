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
