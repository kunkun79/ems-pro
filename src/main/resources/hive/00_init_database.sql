-- ==========================================
-- EMS-Pro 数仓 P2 阶段 —— 00 初始化
-- 目标：创建数仓数据库 ems_dw
-- Hive 版本：2.3.2（docker-compose 集群）
-- ==========================================

CREATE DATABASE IF NOT EXISTS ems_dw
    COMMENT 'EMS-Pro 企业员工效能数据仓库';

USE ems_dw;

-- 创建各层对应的 HDFS 目录（外部表 LOCATION 用）
-- 说明：dfs 命令在 HiveServer2 中执行时以 hive 服务账号权限运行
DFS -mkdir -p /user/hive/warehouse/ems_dw/ods/employee;
DFS -mkdir -p /user/hive/warehouse/ems_dw/ods/department;
DFS -mkdir -p /user/hive/warehouse/ems_dw/ods/attendance;
DFS -mkdir -p /user/hive/warehouse/ems_dw/ods/task;
DFS -mkdir -p /user/hive/warehouse/ems_dw/ods/performance;
DFS -mkdir -p /user/hive/warehouse/ems_dw/dwd;
DFS -mkdir -p /user/hive/warehouse/ems_dw/dws;
DFS -mkdir -p /user/hive/warehouse/ems_dw/ads;
