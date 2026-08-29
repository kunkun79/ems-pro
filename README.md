# 企业管理系统 Pro（EMS-Pro）

一个融合传统企业管理系统与大数据分析能力的全栈项目，基于 Spring Boot 3 构建，集成 ETL 数据管道、Hive 数仓、MapReduce 计算演示等大数据组件，适用于学习 Spring Boot 企业级开发与大数据技术栈。

---

## 目录

- [技术栈](#技术栈)
- [功能特性](#功能特性)
- [项目架构](#项目架构)
- [快速开始](#快速开始)
- [大数据模块说明](#大数据模块说明)
- [目录结构](#目录结构)
- [扩展指南](#扩展指南)

---

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.3.7 | 核心框架 |
| Java | 17 | 运行环境 |
| MyBatis Plus | 3.5.7 | ORM 持久层，内置 CRUD 与分页 |
| Thymeleaf | — | 服务端模板引擎 |
| Hutool | 5.8.28 | Java 工具集 |
| Lombok | — | 简化实体类样板代码 |
| Maven | 3.9+ | 构建工具 |

### 前端

| 技术 | 说明 |
|------|------|
| Bootstrap 5 | 响应式 UI 框架 |
| ECharts 5 | 数据可视化图表库 |
| jQuery | AJAX 交互 |

### 数据库

| 数据库 | 用途 |
|--------|------|
| MySQL 8.0 | 生产环境 |
| H2 | 开发环境（内存数据库，零安装即用） |

### 大数据组件

| 组件 | 说明 |
|------|------|
| HiveSimulator | Hive 数仓模拟器（纯 Java 实现，本地文件存储） |
| HiveRealService | 真实 Hive JDBC 连接（连接 HiveServer2） |
| MapReduceDemo | MapReduce 计算模型演示 |
| ETLPipeline | 抽取 → 转换 → 加载数据管道 |
| Spring Scheduler | 定时调度（每日自动 ETL） |

---

## 功能特性

### 统一登录

管理员与员工共用同一登录入口，根据用户名自动识别身份：

- 输入 `admin` 进入管理后台
- 输入员工用户名进入员工门户
- 两类身份使用独立的 Session，互不干扰

### 管理员后台

- **仪表盘**：员工总数、部门数量、平均薪资、大数据引擎状态等统计卡片，配合性别分布、薪资区间、部门人数、最近操作等多维度图表
- **员工管理**：员工信息的分页展示、关键词搜索、新增、编辑、删除，支持关联部门名称
- **部门管理**：部门的卡片式与表格化展示，支持增删改查
- **大数据分析**：ETL 数据管道、Hive 数仓查询、MapReduce 计算演示
- **数据大屏**：全屏可视化大屏，包含 ETL 流向图、多类统计图表、实时指标
- **个人中心**：管理员修改登录密码

### 员工自助门户

- **注册**：员工自主创建账号，选择所属部门并填写个人信息
- **登录**：使用注册的用户名与密码登录
- **个人信息**：查看个人完整信息
- **修改资料**：修改姓名、性别、年龄、电话、邮箱、地址等个人字段（部门、薪资、在职状态等管理字段不可自行修改）
- **修改密码**：验证旧密码后更新登录密码

### 大数据能力

- **ETL 数据管道**：从业务数据库抽取数据，经数据清洗（空值填充、手机号脱敏）与转换（派生字段计算）后，按日期分区加载至数据仓库
- **双模式 Hive**：默认使用内置 Hive 模拟器快速演示数据流，通过配置一行切换至真实 HiveServer2（HDFS + Parquet 列存）
- **MapReduce 演示**：WordCount 词频统计、按部门薪资汇总、按部门平均年龄等聚合计算
- **定时调度**：支持每日凌晨自动执行 ETL，将增量数据同步至数仓
- **数据分析 API**：操作类型分布、每日操作趋势、每小时操作热力、员工画像、部门效能等统计分析接口

---

## 项目架构

系统采用经典三层架构：

```
Controller（接收请求 / 返回 JSON 或页面）
    ↓
Service（业务逻辑处理）
    ↓
Mapper（MyBatis Plus 数据访问，自动 CRUD）
```

每一层职责单一，模块间通过接口解耦，新增功能时遵循相同模式即可快速扩展。

### 一次请求的完整数据流

```
前端页面 → AJAX 请求 → Controller → Service → Mapper → 数据库 → 统一结果返回
```

---

## 快速开始

### 环境要求

- JDK 17
- Maven 3.9+
- （可选）MySQL 8.0，开发阶段使用 H2 即可

### 启动步骤

1. 克隆项目
```bash
git clone https://github.com/kunkun79/ems-pro.git
```

2. 进入项目目录并编译
```bash
cd ems-pro
mvn compile
```

3. 启动项目
```bash
mvn spring-boot:run
```

4. 访问系统

浏览器打开 `http://localhost:9090`，默认账号 `admin`，密码 `123456`。

> 端口号可在 `src/main/resources/application.yml` 中通过 `server.port` 修改。

### 数据库切换

项目默认使用 H2 内存数据库（开发环境）。切换 MySQL：

1. 执行 `src/main/resources/sql/schema-mysql.sql` 初始化表结构
2. 修改 `application.yml` 中的 `spring.profiles.active` 为 `prod`
3. 配置 MySQL 连接信息后重启即可

---

## 大数据模块说明

### ETL 数据管道（ETLPipeline）

实现数据从业务库到数据仓库的完整流转：

1. **Extract（抽取）**：从业务数据库读取原始数据
2. **Transform（转换）**：数据清洗与加工，包括空值填充、手机号脱敏、工龄等派生字段计算
3. **Load（加载）**：按日期分区将数据加载至数据仓库

### Hive 数仓（HiveSimulator / HiveRealService）

- **模拟器模式**（默认）：纯 Java 实现，用本地文件模拟 HDFS 存储与分区表，演示建表、插入、全表扫描、分组聚合等核心概念
- **真实模式**：通过 JDBC 连接真实 HiveServer2，执行真实 HiveQL，数据存储于 HDFS（Parquet 列存）

两种模式通过配置项 `bigdata.mode` 一键切换。

### MapReduce 计算（MapReduceDemo）

演示 Map → Shuffle → Reduce 三阶段计算模型，提供 WordCount 词频统计、按部门薪资汇总、按部门平均年龄等示例。

### 定时调度（ScheduledTask）

基于 Spring `@Scheduled` 实现，支持配置开启后每日凌晨自动执行 ETL 同步任务。

---

## 目录结构

```
ems-pro/
├── pom.xml                          # Maven 构建配置
├── docker-compose.yml               # Hadoop + Hive 一键部署
├── src/main/java/com/ems/
│   ├── EmsApplication.java          # 启动入口
│   ├── config/                      # 配置层
│   ├── entity/                      # 实体层（数据库表映射）
│   ├── mapper/                      # 数据访问层
│   ├── service/                     # 业务逻辑层（接口 + 实现）
│   ├── controller/                  # 控制器层（页面路由 + REST API）
│   ├── common/                      # 公共工具（统一返回、分页参数）
│   ├── bigdata/                     # 大数据模块
│   │   ├── hive/                    #   Hive 模拟器 / 真实 JDBC 服务
│   │   ├── analysis/                #   MapReduce 演示
│   │   ├── etl/                     #   ETL 数据管道
│   │   └── scheduler/               #   定时调度
│   └── extension/                   # 扩展点说明
└── src/main/resources/
    ├── application.yml              # 主配置
    ├── sql/                         # 建表脚本（H2 / MySQL）
    └── templates/                   # 前端 HTML 页面
```

---

## 扩展指南

新增一个功能模块只需遵循固定模式，以「考勤管理」为例：

1. 创建实体类（`entity/Attendance.java`）
2. 创建 Mapper（`mapper/AttendanceMapper.java`，继承 `BaseMapper`）
3. 创建 Service 接口与实现
4. 创建 Controller（REST API）
5. 在 SQL 脚本中添加建表语句
6. 创建前端 HTML 页面
7. 在 `PageController` 注册路由
8. 在侧边栏添加导航链接

每一层遵循相同模式：复制 → 改名 → 改字段，即可快速完成扩展。

---

## 许可证

本项目仅用于学习与教学目的。
