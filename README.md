# 🚀 企业管理系统 Pro — 从零到一完全教学指南

> **适用人群**：计算机零基础小白 / 想学习Spring Boot + 大数据的学生  
> **所需工具**：VS Code + Java 17 + Maven 3.9（本教程基于你电脑现有环境）  
> **最终成品**：一个带大数据分析能力的企业管理系统

---

## 📋 目录

1. [你的电脑环境现状](#1-你的电脑环境现状)
2. [环境配置与修复](#2-环境配置与修复)
3. [项目架构速览](#3-项目架构速览)
4. [如何在VS Code中打开并运行](#4-如何在vs-code中打开并运行)
5. [代码分层详解（Java后端）](#5-代码分层详解java后端)
6. [大数据模块详解](#6-大数据模块详解)
7. [前端页面说明](#7-前端页面说明)
8. [如何添加新功能（扩展指南）](#8-如何添加新功能扩展指南)
9. [如何安装MySQL并切换数据库](#9-如何安装mysql并切换数据库)
10. [Hadoop/Hive真实环境搭建（进阶）](#10-hadoophive真实环境搭建进阶)
11. [常见问题](#11-常见问题)

---

## 1. 你的电脑环境现状

| 工具 | 状态 | 版本 | 路径 | 说明 |
|------|------|------|------|------|
| **JDK 17** | ✅ 已安装 | 17.0.18 Temurin | `C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot` | 现代Java开发标准 |
| **JDK 8** | ✅ 已安装 | 8u492 | `D:\Java\jdk8\jdk8u492-b09` | 旧版本，不推荐用于本项目 |
| **Maven** | ⚠️ 需配置 | 3.9.16 | `C:\apache-maven-3.9.16` | 构建工具，需修复环境变量 |
| **VS Code** | ✅ 已安装 | 1.129.1 | — | 代码编辑器 |
| **Git** | ✅ 已安装 | 2.54.0 | — | 版本控制 |
| **Node.js** | ✅ 已安装 | 22.22.2 | — | 前端开发备用 |
| **MySQL** | ❌ 未安装 | — | — | 可选，H2可替代 |
| **Docker** | ❌ 未安装 | — | — | 可选，用于Hadoop |

---

## 2. 环境配置与修复

### 2.1 修复 JAVA_HOME（**必须做**）

当前 `JAVA_HOME` 指向 JDK 8，本系统需要 JDK 17。按以下步骤修改：

**步骤1：** 按 `Win + X` → 选择「系统」→ 「高级系统设置」→ 「环境变量」

**步骤2：** 在「系统变量」中找到 `JAVA_HOME`，点「编辑」

**步骤3：** 将值改为：
```
C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot
```

**步骤4：** 在「系统变量」的 `Path` 中，确保有：
```
%JAVA_HOME%\bin
```

**步骤5：** 打开新的命令行窗口，验证：
```cmd
java -version
```
应显示：`OpenJDK version "17.0.18"`

### 2.2 配置 MAVEN_HOME

**步骤1：** 在「系统变量」中新建（或编辑）`MAVEN_HOME`：
```
C:\apache-maven-3.9.16
```

**步骤2：** 在 `Path` 中添加：
```
%MAVEN_HOME%\bin
```

**步骤3：** 验证（**注意：在PowerShell中运行，不是Git Bash**）：
```powershell
mvn -version
```
应显示：`Apache Maven 3.9.16` 和 `Java version: 17.0.18`

> ⚠️ **重要提示**：Git Bash 下 Maven 可能因路径问题报错，请使用 **PowerShell** 或 **CMD** 运行 Maven 命令。

### 2.3 安装 VS Code 插件

打开 VS Code，按 `Ctrl+Shift+X` 打开扩展商店，搜索并安装：

| 插件名 | 用途 | 必装？ |
|--------|------|--------|
| **Extension Pack for Java** | Java开发全家桶（含语法高亮、调试、Maven支持等） | ✅ 必装 |
| **Spring Boot Extension Pack** | Spring Boot专用支持 | ✅ 必装 |
| **Lombok Annotations Support** | Lombok注解支持（@Data等） | ✅ 必装 |
| **Thymeleaf Snippets** | Thymeleaf模板语法高亮 | 推荐 |

安装后重启 VS Code。

---

## 3. 项目架构速览

```
ems-pro/
│
├── pom.xml                          # Maven构建配置（项目的"配方"）
│
├── src/main/java/com/ems/
│   ├── EmsApplication.java          # 🟢 启动入口
│   │
│   ├── config/                      # 🔧 配置层
│   │   ├── MyBatisPlusConfig.java   #    MyBatis Plus配置（分页等）
│   │   └── WebConfig.java           #    跨域配置
│   │
│   ├── entity/                      # 📦 实体层（= 数据库表的Java映射）
│   │   ├── Admin.java               #    管理员
│   │   ├── Department.java          #    部门
│   │   ├── Employee.java            #    员工
│   │   └── OperationLog.java        #    操作日志
│   │
│   ├── mapper/                      # 🗄️ 数据访问层（操作数据库）
│   │   ├── AdminMapper.java         #    继承BaseMapper，自动拥有CRUD
│   │   ├── DepartmentMapper.java
│   │   ├── EmployeeMapper.java      #    含自定义关联查询
│   │   └── OperationLogMapper.java  #    含统计SQL
│   │
│   ├── service/                     # 💼 业务逻辑层
│   │   ├── AdminService.java        #    接口
│   │   ├── EmployeeService.java
│   │   ├── DepartmentService.java
│   │   ├── AnalysisService.java     #    大数据分析接口
│   │   └── impl/                    #    实现类
│   │       ├── AdminServiceImpl.java
│   │       ├── EmployeeServiceImpl.java
│   │       ├── DepartmentServiceImpl.java
│   │       └── AnalysisServiceImpl.java
│   │
│   ├── controller/                  # 🌐 控制器层（接收HTTP请求）
│   │   ├── PageController.java      #    页面路由
│   │   ├── AdminController.java     #    /api/admin/*
│   │   ├── EmployeeController.java  #    /api/employee/*
│   │   ├── DepartmentController.java#    /api/department/*
│   │   ├── AnalysisController.java  #    /api/analysis/*
│   │   └── BigDataController.java   #    /api/bigdata/* ⭐大数据
│   │
│   ├── common/                      # 🧰 公共工具
│   │   ├── Result.java              #    统一返回格式
│   │   └── PageQuery.java           #    分页参数
│   │
│   ├── bigdata/                     # ⭐ 大数据模块
│   │   ├── hive/
│   │   │   └── HiveSimulator.java   #    Hive数据仓库模拟器
│   │   ├── analysis/
│   │   │   └── MapReduceDemo.java   #    MapReduce计算演示
│   │   └── etl/
│   │       └── ETLPipeline.java     #    ETL数据管道
│   │
│   └── extension/                   # 🔌 扩展点
│       └── ExtensionGuide.java      #    教你怎么添加新功能
│
├── src/main/resources/
│   ├── application.yml              # 主配置（数据库、端口等）
│   ├── sql/
│   │   ├── schema-h2.sql            # H2建表（开发用，自动执行）
│   │   ├── data-h2.sql              # H2初始数据
│   │   └── schema-mysql.sql         # MySQL建表（生产用）
│   └── templates/                   # 前端HTML页面
│       ├── login.html               # 登录页（粒子动画）
│       ├── dashboard.html           # 仪表盘（统计图表）
│       ├── employee.html            # 员工管理
│       ├── department.html          # 部门管理
│       ├── bigdata.html             # 大数据分析大屏
│       └── admin.html               # 个人中心
│
├── data/hive/warehouse/             # Hive模拟数据存储
├── .vscode/settings.json            # VS Code配置
└── .gitignore
```

### 数据流（一次请求的完整路径）

```
用户点击「新增员工」按钮
  ↓
前端 HTML (employee.html) → JavaScript 发起 AJAX 请求
  ↓
POST /api/employee  { name: "张三", deptId: 1, ... }
  ↓
EmployeeController.add()  ← 接收请求，调用Service
  ↓
EmployeeServiceImpl.add()  ← 业务校验，调用Mapper
  ↓
EmployeeMapper.insert()  ← MyBatis Plus自动生成SQL
  ↓
INSERT INTO employee (...) VALUES (...)
  ↓
H2/MySQL 数据库存储
  ↓
返回 { code: 200, message: "新增成功" }
  ↓
前端更新表格显示
```

---

## 4. 如何在VS Code中打开并运行

### 4.1 打开项目

**步骤1：** 打开 VS Code

**步骤2：** 点击 `文件 → 打开文件夹`，选择：
```
D:\workbuddy\2026-07-21-14-08-12\ems-pro
```

**步骤3：** 等待右下角提示「Java项目已导入」，这个过程首次可能需要1-2分钟下载依赖。

### 4.2 运行项目

在 VS Code 中有两种方式运行：

**方式一：使用Spring Boot Dashboard（推荐）**

1. 左侧活动栏找到 Spring Boot 图标（叶子图标）
2. 在 `APPS` 面板中看到 `ems-pro`
3. 点击 ▶️ 运行按钮

**方式二：按F5调试运行**

1. 打开 `EmsApplication.java`
2. 在 `main` 方法左侧点击「Run」或按 `F5`

### 4.3 访问系统

启动成功后，控制台会显示：
```
========================================
  企业管理系统 Pro 启动成功！
  访问地址：http://localhost:8080
  H2控制台：http://localhost:8080/h2-console
========================================
```

打开浏览器访问 `http://localhost:8080`：
- 自动跳转到登录页
- 默认账号：`admin` / 密码：`123456`

### 4.4 各页面地址

| 页面 | 地址 | 功能 |
|------|------|------|
| 登录 | http://localhost:8080/login | 粒子动画登录页 |
| 仪表盘 | http://localhost:8080/dashboard | 统计卡片+图表 |
| 员工管理 | http://localhost:8080/employee | 增删改查+分页 |
| 部门管理 | http://localhost:8080/department | 卡片+表格 |
| 大数据分析 | http://localhost:8080/bigdata | ETL+MapReduce+大屏 |
| 个人中心 | http://localhost:8080/admin | 修改密码 |

---

## 5. 代码分层详解（Java后端）

### 5.1 实体层 (entity)

实体类 = 数据库表的Java映射。以 `Employee.java` 为例：

```java
@Data                       // Lombok：自动生成getter/setter/toString
@TableName("employee")      // MyBatis Plus：绑定数据库表
public class Employee {
    @TableId(type = IdType.AUTO)  // 主键自增
    private Integer id;
    private String name;
    private Integer deptId;
    // ... 其他字段
}
```

### 5.2 Mapper层（数据访问）

**重点：** 继承 `BaseMapper<T>` 后，自动获得增删改查+分页能力，**不需要写一行SQL！**

```java
@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {
    // 已自动拥有：insert, deleteById, updateById, selectById, 
    //            selectList, selectPage 等方法
}
```

### 5.3 Service层（业务逻辑）

```java
@Service
public class EmployeeServiceImpl implements EmployeeService {
    @Autowired
    private EmployeeMapper employeeMapper;  // 自动注入Mapper

    public IPage<Employee> pageWithDept(int pageNum, int pageSize, String keyword) {
        Page<Employee> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        
        if (keyword != null) {
            wrapper.like(Employee::getName, keyword);  // WHERE name LIKE '%keyword%'
        }
        return employeeMapper.selectPageWithDept(page, wrapper);
    }
}
```

> **LambdaQueryWrapper 为什么比写SQL好？**
> `wrapper.eq(Employee::getName, "张三")` 是类型安全的。
> 如果你不小心写成 `Employee::getNam`（少了个e），IDE会直接标红报错。
> 而写SQL字符串 `"WHERE nam = '张三'"` 拼写错误只能在运行时才发现。

### 5.4 Controller层（API接口）

```java
@RestController                    // = @Controller + @ResponseBody（返回JSON）
@RequestMapping("/api/employee")   // 所有接口以 /api/employee 开头
public class EmployeeController {

    @GetMapping("/page")           // GET /api/employee/page?page=1&size=10
    public Result<IPage<Employee>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(employeeService.pageWithDept(page, size, null));
    }
}
```

**RESTful API 规范速记：**
- `GET` → 查询
- `POST` → 新增
- `PUT` → 修改
- `DELETE` → 删除

---

## 6. 大数据模块详解

### 6.1 HiveSimulator — Hive数据仓库模拟器

真实Hive是建立在Hadoop之上的数据仓库工具。这个模拟器让你不装Hadoop也能理解Hive的核心概念：

```
真实Hive架构：               本系统模拟：
┌──────────────┐          ┌──────────────────┐
│  HiveQL (SQL) │          │  Java方法调用      │
├──────────────┤          ├──────────────────┤
│  Hive 引擎    │    →    │  HiveSimulator    │
├──────────────┤          ├──────────────────┤
│  MapReduce    │          │  纯Java实现       │
├──────────────┤          ├──────────────────┤
│  HDFS         │          │  本地JSON文件     │
└──────────────┘          └──────────────────┘
```

**核心操作：**

```java
// 1. 创建表（模拟 CREATE TABLE）
hive.createTable("dim_employee", 
    Arrays.asList("id","name","gender","salary"), "dt");

// 2. 插入数据（模拟 INSERT INTO）
hive.insert("dim_employee", rows, "2024-01-15");

// 3. 查询（模拟 SELECT * FROM）
List<Map<String, Object>> result = hive.selectAll("dim_employee");

// 4. 分组聚合（模拟 GROUP BY）
Map<String, Long> deptCount = hive.groupByCount("dim_employee", "dept_id");
```

数据存储位置：`data/hive/warehouse/dim_employee/dt=2024-01-15/data_xxxx.json`

打开这些JSON文件，就能看到Hive"表"里实际存了什么东西。

### 6.2 MapReduceDemo — 理解分布式计算

MapReduce是大数据处理的核心计算模型，分为三个阶段：

**Phase 1: Map（映射）** — 把数据变成 (key, value) 对
```
输入：["张三 技术部", "李四 市场部", "王五 技术部"]
  ↓
Map输出：("技术部", 1), ("市场部", 1), ("技术部", 1)
```

**Phase 2: Shuffle（洗牌）** — 按key分组
```
("技术部", [1, 1])   ← 相同key的value放到一起
("市场部", [1])
```

**Phase 3: Reduce（归约）** — 聚合计算
```
("技术部", 2)   ← 1+1=2
("市场部", 1)
```

**WordCount演示**：在大数据大屏点击「WordCount」按钮，输入任意英文文本，就能看到MapReduce的执行结果。

### 6.3 ETLPipeline — 数据管道

ETL = **E**xtract（抽取） → **T**ransform（转换） → **L**oad（加载）

这是企业级数据处理的标配流程：

```java
// 1. Extract：从业务数据库抽取
List<Employee> employees = employeeMapper.selectList(null);

// 2. Transform：清洗和转换
//    - 空值填充
//    - 手机号脱敏：138****0001
//    - 计算派生字段：工龄 = 今年 - 入职年份

// 3. Load：加载到Hive数仓
hive.insert("dim_employee", transformedRows, "2024-07-21");
```

在大数据大屏点击「执行ETL」按钮，即可看到完整的ETL流程执行。

---

## 7. 前端页面说明

### 技术栈
- **Bootstrap 5**：响应式UI框架（CDN引入，无需下载）
- **ECharts 5**：数据可视化图表库
- **jQuery**：简化AJAX请求
- **Thymeleaf**：Spring Boot自带模板引擎

### 登录页（login.html）
- Canvas实现粒子动画背景（替代原项目的Three.js，更轻量）
- 用户名密码登录，AJAX提交

### 仪表盘（dashboard.html）
- 4个统计卡片：员工总数、部门数、平均薪资、引擎状态
- 3个ECharts图表：性别饼图、薪资柱状图、部门人数图
- 最近操作列表

### 员工管理（employee.html）
- Bootstrap表格+分页
- 模态框实现新增/编辑
- 部门下拉框动态加载
- 搜索+删除确认

### 大数据大屏（bigdata.html）⭐
- 深色科技风主题
- 每日操作趋势折线图
- 每小时热力面积图
- 员工画像饼图
- 薪资MapReduce结果柱状图
- WordCount交互演示
- ETL一键执行

---

## 8. 如何添加新功能（扩展指南）

假设你想添加「**考勤管理**」模块，按以下步骤：

### 步骤1：创建实体类
```java
// src/main/java/com/ems/entity/Attendance.java
@Data
@TableName("attendance")
public class Attendance {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer empId;       // 员工ID
    private LocalDate date;      // 日期
    private String status;       // 正常/迟到/早退/缺勤
    private LocalTime checkIn;   // 签到时间
    private LocalTime checkOut;  // 签退时间
}
```

### 步骤2：创建Mapper
```java
@Mapper
public interface AttendanceMapper extends BaseMapper<Attendance> {
    // BaseMapper自动提供全部CRUD
}
```

### 步骤3：创建Service + 实现
```java
public interface AttendanceService {
    boolean add(Attendance attendance);
    List<Attendance> getByEmpId(Integer empId);
}
```

### 步骤4：创建Controller
```java
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController { ... }
```

### 步骤5：添加数据库表
在 `schema-h2.sql` 和 `schema-mysql.sql` 中添加建表语句。

### 步骤6：创建前端页面
复制 `employee.html` 改名为 `attendance.html`，修改表格列和表单字段。

### 步骤7：注册路由
在 `PageController.java` 中添加：
```java
@GetMapping("/attendance")
public String attendance() { return "attendance"; }
```

### 步骤8：添加导航
在每个HTML页面的侧边栏中添加链接：
```html
<a href="/attendance"><i class="bi bi-calendar-check"></i> 考勤管理</a>
```

**关键原则：每层都遵循相同的模式，复制 → 改名 → 改字段 → 搞定！**

---

## 9. 如何安装MySQL并切换数据库

### 9.1 安装MySQL

**方式一（推荐）：使用安装包**

1. 访问 https://dev.mysql.com/downloads/installer/
2. 下载 `mysql-installer-community-8.0.x.msi`
3. 运行安装，选择「Developer Default」
4. 设置root密码为 `123456`（或你记得住的密码）
5. 一路Next完成安装

**方式二：使用免安装版**

1. 下载 MySQL 8.0 ZIP 包
2. 解压到 `D:\mysql-8.0`
3. 在 `D:\mysql-8.0` 下创建 `my.ini`：
```ini
[mysqld]
basedir=D:/mysql-8.0
datadir=D:/mysql-8.0/data
port=3306
```
4. 管理员CMD执行：
```cmd
D:\mysql-8.0\bin\mysqld --initialize --console
D:\mysql-8.0\bin\mysqld --install
net start mysql
```

### 9.2 创建数据库和导入数据

```cmd
mysql -u root -p
```

```sql
-- 方法一：直接执行SQL文件
source D:/workbuddy/2026-07-21-14-08-12/ems-pro/src/main/resources/sql/schema-mysql.sql;

-- 方法二：手动执行
CREATE DATABASE ems_pro;
-- 然后复制 schema-mysql.sql 内容粘贴执行
```

### 9.3 切换到MySQL

修改 `application.yml`：

```yaml
spring:
  profiles:
    active: prod    # 改这里！从 dev 改为 prod
```

启动项目，系统会自动连接 MySQL。

> 💡 **提示**：开发阶段用H2内存数据库最方便（免安装、自动初始化），
> 生产部署时再切换到MySQL。

---

## 10. Hadoop/Hive真实环境搭建（进阶）

> ⚠️ 这一部分适合你已经熟悉了基本开发后尝试。Hadoop在Windows上很难原生安装，
> 推荐使用Docker或WSL。

### 10.1 使用Docker搭建（最简单）

**步骤1：** 安装 Docker Desktop
- 下载：https://www.docker.com/products/docker-desktop/
- 安装后确保Docker正在运行

**步骤2：** 拉取Hadoop+Hive镜像
```cmd
docker pull bde2020/hadoop-namenode:2.0.0-hadoop3.2.1-java8
docker pull bde2020/hadoop-datanode:2.0.0-hadoop3.2.1-java8
docker pull bde2020/hive:2.3.2-postgresql-metastore
```

**步骤3：** 使用docker-compose启动集群
参考项目中的 `docker/docker-compose.yml`（待创建）

### 10.2 接入Java项目

在 `pom.xml` 中添加Hive JDBC依赖：
```xml
<dependency>
    <groupId>org.apache.hive</groupId>
    <artifactId>hive-jdbc</artifactId>
    <version>3.1.3</version>
</dependency>
```

然后在Service中直接写SQL查询Hive：
```java
Connection conn = DriverManager.getConnection("jdbc:hive2://localhost:10000/default");
Statement stmt = conn.createStatement();
ResultSet rs = stmt.executeQuery("SELECT dept_id, COUNT(*) FROM dim_employee GROUP BY dept_id");
```

---

## 11. 常见问题

### Q1: Maven报错「找不到或无法加载主类 Launcher」
**原因**：Git Bash下的路径问题  
**解决**：在PowerShell中运行Maven命令，而不是Git Bash

### Q2: 端口8080被占用
**解决**：修改 `application.yml` 中的 `server.port` 为其他值如 `8081`

### Q3: H2数据库数据丢失
**说明**：H2是内存数据库，重启后数据会丢失  
**解决**：开发阶段每次重启都会自动执行SQL初始化，不影响使用。需要持久化请切换到MySQL。

### Q4: Lombok注解不生效（@Data报红）
**解决**：
1. 确认已安装 Lombok Annotations Support 插件
2. VS Code中按 `Ctrl+Shift+P` → 搜索 `Java: Clean Java Language Server Workspace` → 执行

### Q5: 要怎么学习这个项目？
建议学习路径：
1. **先用起来**：启动项目，把所有页面功能点一遍，感受整体
2. **看代码**：打开 `EmployeeController` → `EmployeeService` → `EmployeeMapper` → `Employee`，追踪一次请求的完整路径
3. **改点东西**：比如给员工表加一个「学历」字段，在前后端都加上
4. **加新模块**：按照第8章的步骤，试着添加考勤/项目/合同等模块
5. **玩大数据**：在大屏上执行ETL和MapReduce，看数据流转
6. **搭真实环境**：尝试Docker部署Hadoop+Hive

---

## 📚 延伸学习资源

| 主题 | 推荐资源 |
|------|----------|
| Spring Boot 基础 | B站搜索「Spring Boot 3 教程」 |
| MyBatis Plus | 官方文档：https://baomidou.com |
| ECharts 图表 | 官方示例：https://echarts.apache.org/examples/ |
| Hadoop 入门 | 《Hadoop权威指南》 |
| Hive 入门 | 《Hive编程指南》 |
| Bootstrap 5 | 官方文档：https://getbootstrap.com |

---

> 🎉 **恭喜！你已经拥有一个完整的、可扩展的、带大数据能力的企业管理系统。**
> 
> 从登录页的粒子动画，到大屏的数据可视化，到Hive模拟器，
> 到MapReduce计算演示，到ETL数据管道——这个项目覆盖了现代企业级开发的核心知识点。
> 
> **下一步是什么？你来决定。**
