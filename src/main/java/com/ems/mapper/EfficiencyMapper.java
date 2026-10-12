package com.ems.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 员工效能分析 Mapper
 *
 * 【设计说明】
 * 1. 所有查询都以「聚合下推」为原则：分组、求和、求平均全部交给数据库完成，
 *    Java 侧只做结果合并与效能指数计算，避免把十几万行明细拉进内存。
 * 2. SQL 只使用 MySQL 8 与 H2 2.x 同时支持的标准写法：
 *    - 年月截取用 SUBSTRING(CAST(date AS CHAR(10)),1,7)，避开 MySQL 独有的 DATE_FORMAT；
 *    - 条件统计用 CASE WHEN，避开 IF()/IFNULL() 等方言函数；
 *    - 数值比较符号只在 script 动态 SQL 块内才写成 &gt; / &lt;，
 *      普通 @Select 注解不走 XML 解析，必须直接写 &gt; 的原字符，否则报语法错误；
 * 3. 每个方法都支持可选的 deptId，为空时统计全公司，非空时按部门过滤。
 * 4. 返回 Map 而非实体：列别名统一用下划线命名，由 Service 归一化为小写 key，
 *    从而屏蔽 MySQL（原样返回）与 H2（返回大写列名）的差异。
 */
@Mapper
public interface EfficiencyMapper {

    // ==================== 总览 ====================

    /**
     * 总览指标：员工数、三类事实表记录数、出勤率、完工率、平均分、数据覆盖区间
     */
    @Select("<script>SELECT " +
            "(SELECT COUNT(*) FROM employee WHERE 1 = 1" +
            " <if test='deptId != null'> AND dept_id = #{deptId}</if>) AS employee_count, " +
            "(SELECT COUNT(*) FROM attendance a" +
            " <if test='deptId != null'> JOIN employee e ON e.id = a.emp_id AND e.dept_id = #{deptId}</if>) AS attendance_count, " +
            "(SELECT COUNT(*) FROM task t" +
            " <if test='deptId != null'> JOIN employee e ON e.id = t.emp_id AND e.dept_id = #{deptId}</if>) AS task_count, " +
            "(SELECT COUNT(*) FROM performance p" +
            " <if test='deptId != null'> JOIN employee e ON e.id = p.emp_id AND e.dept_id = #{deptId}</if>) AS performance_count, " +
            "(SELECT ROUND(100.0 * SUM(CASE WHEN a.leave_type = '正常' THEN 1 ELSE 0 END) / COUNT(*), 2)" +
            " FROM attendance a" +
            " <if test='deptId != null'> JOIN employee e ON e.id = a.emp_id AND e.dept_id = #{deptId}</if>) AS attendance_rate, " +
            "(SELECT ROUND(100.0 * SUM(CASE WHEN t.status = '已完成' THEN 1 ELSE 0 END) /" +
            " SUM(CASE WHEN t.status &lt;&gt; '已取消' THEN 1 ELSE 0 END), 2)" +
            " FROM task t" +
            " <if test='deptId != null'> JOIN employee e ON e.id = t.emp_id AND e.dept_id = #{deptId}</if>) AS completion_rate, " +
            "(SELECT ROUND(AVG(p.kpi_score), 2) FROM performance p" +
            " <if test='deptId != null'> JOIN employee e ON e.id = p.emp_id AND e.dept_id = #{deptId}</if>) AS avg_kpi, " +
            "(SELECT ROUND(AVG(CASE WHEN t.status = '已完成' THEN t.quality_score END), 2) FROM task t" +
            " <if test='deptId != null'> JOIN employee e ON e.id = t.emp_id AND e.dept_id = #{deptId}</if>) AS avg_quality, " +
            "(SELECT ROUND(AVG(a.overtime_hours), 2) FROM attendance a" +
            " <if test='deptId != null'> JOIN employee e ON e.id = a.emp_id AND e.dept_id = #{deptId}</if>) AS avg_overtime, " +
            "(SELECT ROUND(AVG(a.work_hours), 2) FROM attendance a" +
            " <if test='deptId != null'> JOIN employee e ON e.id = a.emp_id AND e.dept_id = #{deptId}</if>) AS avg_work_hours, " +
            "(SELECT MIN(a.work_date) FROM attendance a) AS min_date, " +
            "(SELECT MAX(a.work_date) FROM attendance a) AS max_date, " +
            "(SELECT COUNT(*) FROM task WHERE quality_score &gt; 10) AS dirty_quality_count" +
            "</script>")
    Map<String, Object> overview(@Param("deptId") Integer deptId);

    // ==================== 月度趋势 ====================

    /**
     * 月度考勤趋势：出勤率、迟到天数、平均加班、平均工时
     */
    @Select("<script>SELECT SUBSTRING(CAST(a.work_date AS CHAR(10)), 1, 7) AS month, " +
            "COUNT(*) AS total_days, " +
            "SUM(CASE WHEN a.leave_type = '正常' THEN 1 ELSE 0 END) AS normal_days, " +
            "SUM(CASE WHEN a.leave_type = '正常' AND a.late_minutes &gt; 0 THEN 1 ELSE 0 END) AS late_days, " +
            "ROUND(SUM(a.late_minutes), 2) AS late_minutes, " +
            "ROUND(AVG(a.overtime_hours), 2) AS avg_overtime, " +
            "ROUND(AVG(a.work_hours), 2) AS avg_work_hours " +
            "FROM attendance a " +
            "<if test='deptId != null'>JOIN employee e ON e.id = a.emp_id AND e.dept_id = #{deptId}</if> " +
            "GROUP BY SUBSTRING(CAST(a.work_date AS CHAR(10)), 1, 7) " +
            "ORDER BY month</script>")
    List<Map<String, Object>> monthlyAttendance(@Param("deptId") Integer deptId);

    /**
     * 月度任务趋势：完成率、延期率、平均质量分
     */
    @Select("<script>SELECT SUBSTRING(CAST(t.assign_date AS CHAR(10)), 1, 7) AS month, " +
            "COUNT(*) AS total_task, " +
            "SUM(CASE WHEN t.status = '已完成' THEN 1 ELSE 0 END) AS finished_task, " +
            "SUM(CASE WHEN t.status = '已取消' THEN 1 ELSE 0 END) AS canceled_task, " +
            "SUM(CASE WHEN t.status = '已延期' THEN 1 ELSE 0 END) AS delayed_task, " +
            "SUM(CASE WHEN t.status = '进行中' THEN 1 ELSE 0 END) AS doing_task, " +
            "ROUND(AVG(CASE WHEN t.status = '已完成' AND t.quality_score &lt;= 10 THEN t.quality_score END), 2) AS avg_quality " +
            "FROM task t " +
            "<if test='deptId != null'>JOIN employee e ON e.id = t.emp_id AND e.dept_id = #{deptId}</if> " +
            "GROUP BY SUBSTRING(CAST(t.assign_date AS CHAR(10)), 1, 7) " +
            "ORDER BY month</script>")
    List<Map<String, Object>> monthlyTask(@Param("deptId") Integer deptId);

    /**
     * 月度绩效趋势：平均 KPI、优秀（S+A）占比
     */
    @Select("<script>SELECT p.period AS month, " +
            "COUNT(*) AS perf_count, " +
            "ROUND(AVG(p.kpi_score), 2) AS avg_kpi, " +
            "SUM(CASE WHEN p.grade = 'S' OR p.grade = 'A' THEN 1 ELSE 0 END) AS excellent_count " +
            "FROM performance p " +
            "<if test='deptId != null'>JOIN employee e ON e.id = p.emp_id AND e.dept_id = #{deptId}</if> " +
            "GROUP BY p.period " +
            "ORDER BY month</script>")
    List<Map<String, Object>> monthlyPerformance(@Param("deptId") Integer deptId);

    // ==================== 部门维度 ====================

    /**
     * 部门 × 考勤聚合
     */
    @Select("SELECT e.dept_id AS dept_id, " +
            "COUNT(*) AS total_days, " +
            "SUM(CASE WHEN a.leave_type = '正常' THEN 1 ELSE 0 END) AS normal_days, " +
            "SUM(CASE WHEN a.leave_type = '正常' AND a.late_minutes > 0 THEN 1 ELSE 0 END) AS late_days, " +
            "ROUND(AVG(a.overtime_hours), 2) AS avg_overtime, " +
            "ROUND(AVG(a.work_hours), 2) AS avg_work_hours " +
            "FROM attendance a JOIN employee e ON e.id = a.emp_id " +
            "GROUP BY e.dept_id")
    List<Map<String, Object>> deptAttendance();

    /**
     * 部门 × 任务聚合
     */
    @Select("SELECT e.dept_id AS dept_id, " +
            "COUNT(*) AS total_task, " +
            "SUM(CASE WHEN t.status = '已完成' THEN 1 ELSE 0 END) AS finished_task, " +
            "SUM(CASE WHEN t.status = '已取消' THEN 1 ELSE 0 END) AS canceled_task, " +
            "SUM(CASE WHEN t.status = '已延期' THEN 1 ELSE 0 END) AS delayed_task, " +
            "ROUND(AVG(CASE WHEN t.status = '已完成' AND t.quality_score <= 10 THEN t.quality_score END), 2) AS avg_quality " +
            "FROM task t JOIN employee e ON e.id = t.emp_id " +
            "GROUP BY e.dept_id")
    List<Map<String, Object>> deptTask();

    /**
     * 部门 × 绩效聚合
     */
    @Select("SELECT e.dept_id AS dept_id, " +
            "COUNT(*) AS perf_count, " +
            "ROUND(AVG(p.kpi_score), 2) AS avg_kpi, " +
            "SUM(CASE WHEN p.grade = 'S' OR p.grade = 'A' THEN 1 ELSE 0 END) AS excellent_count " +
            "FROM performance p JOIN employee e ON e.id = p.emp_id " +
            "GROUP BY e.dept_id")
    List<Map<String, Object>> deptPerformance();

    /**
     * 部门在册人数
     */
    @Select("SELECT dept_id AS dept_id, COUNT(*) AS emp_count " +
            "FROM employee WHERE dept_id IS NOT NULL GROUP BY dept_id")
    List<Map<String, Object>> deptEmpCount();

    // ==================== 结构分布 ====================

    /**
     * 绩效等级分布：S / A / B / C / D
     */
    @Select("<script>SELECT p.grade AS grade, COUNT(*) AS cnt " +
            "FROM performance p " +
            "<if test='deptId != null'>JOIN employee e ON e.id = p.emp_id AND e.dept_id = #{deptId}</if> " +
            "GROUP BY p.grade</script>")
    List<Map<String, Object>> gradeDistribution(@Param("deptId") Integer deptId);

    /**
     * 考勤状态构成：正常 / 事假 / 病假 / 年假 / 调休 / 旷工
     */
    @Select("<script>SELECT a.leave_type AS leave_type, COUNT(*) AS cnt " +
            "FROM attendance a " +
            "<if test='deptId != null'>JOIN employee e ON e.id = a.emp_id AND e.dept_id = #{deptId}</if> " +
            "GROUP BY a.leave_type</script>")
    List<Map<String, Object>> leaveTypeDistribution(@Param("deptId") Integer deptId);

    /**
     * 任务类型分布：各类工单的派发量、完工量与质量分
     */
    @Select("<script>SELECT t.task_type AS task_type, " +
            "COUNT(*) AS total_task, " +
            "SUM(CASE WHEN t.status = '已完成' THEN 1 ELSE 0 END) AS finished_task, " +
            "SUM(CASE WHEN t.status = '已取消' THEN 1 ELSE 0 END) AS canceled_task, " +
            "ROUND(AVG(CASE WHEN t.status = '已完成' AND t.quality_score &lt;= 10 THEN t.quality_score END), 2) AS avg_quality " +
            "FROM task t " +
            "<if test='deptId != null'>JOIN employee e ON e.id = t.emp_id AND e.dept_id = #{deptId}</if> " +
            "GROUP BY t.task_type " +
            "ORDER BY total_task DESC</script>")
    List<Map<String, Object>> taskTypeDistribution(@Param("deptId") Integer deptId);

    /**
     * 热力图：部门 × 月份 出勤率
     */
    @Select("SELECT e.dept_id AS dept_id, " +
            "SUBSTRING(CAST(a.work_date AS CHAR(10)), 1, 7) AS month, " +
            "COUNT(*) AS total_days, " +
            "SUM(CASE WHEN a.leave_type = '正常' THEN 1 ELSE 0 END) AS normal_days " +
            "FROM attendance a JOIN employee e ON e.id = a.emp_id " +
            "GROUP BY e.dept_id, SUBSTRING(CAST(a.work_date AS CHAR(10)), 1, 7) " +
            "ORDER BY e.dept_id, month")
    List<Map<String, Object>> deptMonthHeatmap();

    // ==================== 员工维度 ====================

    @Select("<script>SELECT e.id AS emp_id, e.name AS emp_name, e.dept_id AS dept_id, " +
            "COUNT(*) AS total_days, " +
            "SUM(CASE WHEN a.leave_type = '正常' THEN 1 ELSE 0 END) AS normal_days, " +
            "SUM(CASE WHEN a.leave_type = '正常' AND a.late_minutes &gt; 0 THEN 1 ELSE 0 END) AS late_days, " +
            "ROUND(AVG(a.overtime_hours), 2) AS avg_overtime " +
            "FROM attendance a JOIN employee e ON e.id = a.emp_id " +
            "<if test='deptId != null'>AND e.dept_id = #{deptId} </if>" +
            "GROUP BY e.id, e.name, e.dept_id</script>")
    List<Map<String, Object>> empAttendance(@Param("deptId") Integer deptId);

    @Select("<script>SELECT e.id AS emp_id, " +
            "COUNT(*) AS total_task, " +
            "SUM(CASE WHEN t.status = '已完成' THEN 1 ELSE 0 END) AS finished_task, " +
            "SUM(CASE WHEN t.status = '已取消' THEN 1 ELSE 0 END) AS canceled_task, " +
            "ROUND(AVG(CASE WHEN t.status = '已完成' AND t.quality_score &lt;= 10 THEN t.quality_score END), 2) AS avg_quality " +
            "FROM task t JOIN employee e ON e.id = t.emp_id " +
            "<if test='deptId != null'>AND e.dept_id = #{deptId} </if>" +
            "GROUP BY e.id</script>")
    List<Map<String, Object>> empTask(@Param("deptId") Integer deptId);

    @Select("<script>SELECT e.id AS emp_id, " +
            "COUNT(*) AS perf_count, " +
            "ROUND(AVG(p.kpi_score), 2) AS avg_kpi " +
            "FROM performance p JOIN employee e ON e.id = p.emp_id " +
            "<if test='deptId != null'>AND e.dept_id = #{deptId} </if>" +
            "GROUP BY e.id</script>")
    List<Map<String, Object>> empPerformance(@Param("deptId") Integer deptId);
}
