-- ==========================================
-- 员工门户 — MySQL 升级迁移脚本
-- 适用场景：已有 ems_pro 数据库，新增员工门户功能
-- 执行方式：mysql -u root -p < migrate-emp-portal.sql --default-character-set=utf8mb4
-- ==========================================

USE ems_pro;

-- 1. 添加 username 和 password 字段
ALTER TABLE employee
    ADD COLUMN username VARCHAR(50) COMMENT '员工门户用户名' AFTER status,
    ADD COLUMN password VARCHAR(100) COMMENT '员工门户密码' AFTER username,
    ADD INDEX idx_username (username);

-- 2. 为已有员工生成默认用户名（用手机号作为默认用户名，方便已知员工登录）
UPDATE employee
SET username = phone
WHERE username IS NULL AND phone IS NOT NULL AND phone != '';

-- 3. 为已有员工设置默认密码（123456，建议首次登录后修改）
UPDATE employee
SET password = '123456'
WHERE password IS NULL AND username IS NOT NULL;
