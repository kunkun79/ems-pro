package com.ems.extension;

/**
 * 扩展点接口 —— 教你如何添加新功能
 *
 * 【学习要点——如何扩展这个系统？】
 *
 * 1. 添加新的实体/表：
 *    - 在 entity/ 下新建类，用 @TableName 指定表名
 *    - 在 mapper/ 下新建接口 extends BaseMapper<你的实体>
 *    - 在 service/ 下新建接口和实现
 *    - 在 controller/ 下新建Controller
 *    - 在 sql/schema-h2.sql 中添加建表语句
 *
 * 2. 添加新的大数据分析功能：
 *    - 在 bigdata/analysis/ 下新建分析类
 *    - 在 BigDataController 中添加新的API端点
 *    - 在前端大屏中添加新的图表
 *
 * 3. 添加定时任务：
 *    - 在 config/ 下新建 ScheduleConfig
 *    - 用 @Scheduled 注解定义定时任务
 *
 * 4. 切换数据库：
 *    - 安装MySQL → 创建ems_pro数据库 → 修改application.yml的profiles.active=prod
 *
 * 5. 接入真实Hadoop/Hive（进阶）：
 *    - 见 docs/hadoop-setup-guide.md
 *
 * 示例：添加一个「考勤管理」模块
 * ==========================================
 * 步骤1：创建考勤实体 Attendance.java
 * 步骤2：创建 AttendanceMapper.java
 * 步骤3：创建 AttendanceService.java + impl
 * 步骤4：创建 AttendanceController.java
 * 步骤5：在schema-h2.sql添加attendance表
 * 步骤6：创建 attendance.html 页面
 * 步骤7：在 PageController 添加路由
 * 步骤8：在侧边栏添加导航链接
 *
 * 就这么简单！每层都遵循相同的模式。
 */
public class ExtensionGuide {
    /*
     * 这个类只是一个「扩展点说明」，不包含实际代码。
     * 你可以删除它，或在这里存放工具类、常量等。
     *
     * 常见的扩展方式：
     *
     * ┌─────────────────────────────────────────────────┐
     * │  你想要的扩展                    怎么做          │
     * ├─────────────────────────────────────────────────┤
     * │  加一个新页面                   新增 entity +    │
     * │  （如：考勤/项目/合同）          mapper +        │
     * │                                  service +       │
     * │                                  controller +    │
     * │                                  html模板        │
     * ├─────────────────────────────────────────────────┤
     * │  添加图表/数据分析              在AnalysisService│
     * │                                  中添加统计方法  │
     * │                                  在前端添加      │
     * │                                  ECharts配置     │
     * ├─────────────────────────────────────────────────┤
     * │  接入外部数据源                 在bigdata/etl/   │
     * │  （API/文件/消息队列）          添加新的ETL类    │
     * ├─────────────────────────────────────────────────┤
     * │  添加用户权限                   引入Spring       │
     * │                                  Security        │
     * │                                  或自建RBAC      │
     * ├─────────────────────────────────────────────────┤
     * │  改用前后端分离                 前端改用Vue/     │
     * │                                  React           │
     * │                                  后端API不变     │
     * └─────────────────────────────────────────────────┘
     */
}
