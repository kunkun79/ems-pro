package com.ems.service;

import com.ems.entity.Department;
import com.ems.mapper.DepartmentMapper;
import com.ems.mapper.EfficiencyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 员工效能分析服务
 *
 * 【职责】
 * 聚合查询由 EfficiencyMapper 下推到数据库完成，本服务只做三件事：
 *   1. 归一化不同数据库返回的列名（MySQL 原样返回，H2 返回大写列名）；
 *   2. 把「考勤 / 任务 / 绩效」三条事实线的聚合结果按维度（月份、部门、员工）合并；
 *   3. 按统一权重计算综合效能指数。
 *
 * 【综合效能指数权重】（与 Hive DWS 层 dws_efficiency_wide 保持一致）
 *   0.30 × 任务完成率 + 0.25 × KPI 得分 + 0.20 × 出勤率 + 0.15 × 准时率 + 0.10 × 质量分
 * 五项指标统一折算到百分制后加权，结果落在 0~100 区间，便于跨部门横向对比。
 */
@Service
public class EfficiencyService {

    /** 综合效能指数权重 */
    private static final double W_COMPLETE = 0.30;
    private static final double W_KPI      = 0.25;
    private static final double W_ATTEND   = 0.20;
    private static final double W_ONTIME   = 0.15;
    private static final double W_QUALITY  = 0.10;

    @Autowired
    private EfficiencyMapper efficiencyMapper;

    @Autowired
    private DepartmentMapper departmentMapper;

    // ==================================================================
    //  总览
    // ==================================================================

    /**
     * 总览卡片数据
     */
    public Map<String, Object> overview(Integer deptId) {
        Map<String, Object> row = lowerRow(efficiencyMapper.overview(deptId));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("employeeCount", lng(row, "employee_count"));
        res.put("attendanceCount", lng(row, "attendance_count"));
        res.put("taskCount", lng(row, "task_count"));
        res.put("performanceCount", lng(row, "performance_count"));
        res.put("attendanceRate", dbl(row, "attendance_rate"));
        res.put("completionRate", dbl(row, "completion_rate"));
        res.put("avgKpi", dbl(row, "avg_kpi"));
        // 库里质量分是 0~10 分制，展示时折算为百分制
        res.put("avgQuality", round2(dbl(row, "avg_quality") * 10));
        res.put("avgOvertime", dbl(row, "avg_overtime"));
        res.put("avgWorkHours", dbl(row, "avg_work_hours"));
        res.put("dateFrom", str(row, "min_date"));
        res.put("dateTo", str(row, "max_date"));
        res.put("dirtyQualityCount", lng(row, "dirty_quality_count"));
        return res;
    }

    // ==================================================================
    //  月度趋势
    // ==================================================================

    /**
     * 月度效能趋势：出勤率 / 完成率 / KPI / 加班 / 质量分 随 12 个月的变化
     */
    public List<Map<String, Object>> trend(Integer deptId) {
        Map<String, Map<String, Object>> rows = new TreeMap<>();

        for (Map<String, Object> r : efficiencyMapper.monthlyAttendance(deptId)) {
            Map<String, Object> m = lowerRow(r);
            String month = str(m, "month");
            Map<String, Object> row = rows.computeIfAbsent(month, k -> new LinkedHashMap<>());
            row.put("month", month);
            double total = dbl(m, "total_days");
            double normal = dbl(m, "normal_days");
            double late = dbl(m, "late_days");
            row.put("attendanceRate", rate(normal, total));
            row.put("onTimeRate", rate(normal - late, normal));
            row.put("lateDays", lng(m, "late_days"));
            row.put("avgOvertime", dbl(m, "avg_overtime"));
            row.put("avgWorkHours", dbl(m, "avg_work_hours"));
        }

        for (Map<String, Object> r : efficiencyMapper.monthlyTask(deptId)) {
            Map<String, Object> m = lowerRow(r);
            String month = str(m, "month");
            Map<String, Object> row = rows.computeIfAbsent(month, k -> new LinkedHashMap<>());
            row.put("month", month);
            double total = dbl(m, "total_task");
            double canceled = dbl(m, "canceled_task");
            double valid = total - canceled;
            row.put("taskCount", lng(m, "total_task"));
            row.put("completionRate", rate(dbl(m, "finished_task"), valid));
            row.put("delayedRate", rate(dbl(m, "delayed_task"), valid));
            row.put("avgQuality", round2(dbl(m, "avg_quality") * 10));
        }

        for (Map<String, Object> r : efficiencyMapper.monthlyPerformance(deptId)) {
            Map<String, Object> m = lowerRow(r);
            String month = str(m, "month");
            Map<String, Object> row = rows.computeIfAbsent(month, k -> new LinkedHashMap<>());
            row.put("month", month);
            row.put("avgKpi", dbl(m, "avg_kpi"));
            row.put("excellentRate", rate(dbl(m, "excellent_count"), dbl(m, "perf_count")));
        }

        return new ArrayList<>(rows.values());
    }

    // ==================================================================
    //  部门维度
    // ==================================================================

    /**
     * 部门效能排行（按综合效能指数降序）
     */
    public List<Map<String, Object>> deptRank(int limit) {
        Map<Integer, String> deptNames = deptNames();

        Map<Integer, Map<String, Object>> rows = new LinkedHashMap<>();
        for (Map<String, Object> r : efficiencyMapper.deptEmpCount()) {
            Map<String, Object> m = lowerRow(r);
            int deptId = (int) lng(m, "dept_id");
            Map<String, Object> row = rows.computeIfAbsent(deptId, k -> new LinkedHashMap<>());
            row.put("deptId", deptId);
            row.put("deptName", deptNames.getOrDefault(deptId, "部门" + deptId));
            row.put("employeeCount", lng(m, "emp_count"));
        }

        for (Map<String, Object> r : efficiencyMapper.deptAttendance()) {
            int deptId = (int) lng(lowerRow(r), "dept_id");
            Map<String, Object> m = lowerRow(r);
            Map<String, Object> row = rows.computeIfAbsent(deptId, k -> baseDeptRow(deptId, deptNames));
            double total = dbl(m, "total_days");
            double normal = dbl(m, "normal_days");
            double late = dbl(m, "late_days");
            row.put("attendanceRate", rate(normal, total));
            row.put("onTimeRate", rate(normal - late, normal));
            row.put("avgOvertime", dbl(m, "avg_overtime"));
            row.put("avgWorkHours", dbl(m, "avg_work_hours"));
        }

        for (Map<String, Object> r : efficiencyMapper.deptTask()) {
            Map<String, Object> m = lowerRow(r);
            int deptId = (int) lng(m, "dept_id");
            Map<String, Object> row = rows.computeIfAbsent(deptId, k -> baseDeptRow(deptId, deptNames));
            double valid = dbl(m, "total_task") - dbl(m, "canceled_task");
            row.put("taskCount", lng(m, "total_task"));
            row.put("completionRate", rate(dbl(m, "finished_task"), valid));
            row.put("delayedRate", rate(dbl(m, "delayed_task"), valid));
            row.put("avgQuality", round2(dbl(m, "avg_quality") * 10));
        }

        for (Map<String, Object> r : efficiencyMapper.deptPerformance()) {
            Map<String, Object> m = lowerRow(r);
            int deptId = (int) lng(m, "dept_id");
            Map<String, Object> row = rows.computeIfAbsent(deptId, k -> baseDeptRow(deptId, deptNames));
            row.put("avgKpi", dbl(m, "avg_kpi"));
            row.put("excellentRate", rate(dbl(m, "excellent_count"), dbl(m, "perf_count")));
        }

        List<Map<String, Object>> list = new ArrayList<>(rows.values());
        list.forEach(row -> row.put("score", score(row)));
        list.sort((a, b) -> Double.compare(dbl(b, "score"), dbl(a, "score")));
        for (int i = 0; i < list.size(); i++) {
            list.get(i).put("rank", i + 1);
        }
        return limit > 0 && list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
    }

    /**
     * 部门 × 月份 出勤率热力图
     */
    public Map<String, Object> heatmap() {
        Map<Integer, String> deptNames = deptNames();
        Set<String> months = new TreeSet<>();
        List<Map<String, Object>> raw = new ArrayList<>();

        for (Map<String, Object> r : efficiencyMapper.deptMonthHeatmap()) {
            Map<String, Object> m = lowerRow(r);
            String month = str(m, "month");
            int deptId = (int) lng(m, "dept_id");
            double rate = rate(dbl(m, "normal_days"), dbl(m, "total_days"));
            months.add(month);
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("deptId", deptId);
            cell.put("month", month);
            cell.put("rate", rate);
            raw.add(cell);
        }

        List<String> monthList = new ArrayList<>(months);
        List<Integer> deptList = new ArrayList<>(new TreeSet<>(deptNames.keySet()));
        Map<Integer, Integer> deptIndex = new HashMap<>();
        for (int i = 0; i < deptList.size(); i++) {
            deptIndex.put(deptList.get(i), i);
        }

        // ECharts 热力图数据格式：[月份下标, 部门下标, 出勤率]
        List<List<Object>> data = new ArrayList<>();
        for (Map<String, Object> c : raw) {
            Integer di = deptIndex.get((Integer) c.get("deptId"));
            int mi = monthList.indexOf(c.get("month"));
            if (di == null || mi < 0) {
                continue;
            }
            data.add(Arrays.asList(mi, di, c.get("rate")));
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("months", monthList);
        List<String> deptLabels = new ArrayList<>();
        for (Integer id : deptList) {
            deptLabels.add(deptNames.getOrDefault(id, "部门" + id));
        }
        res.put("depts", deptLabels);
        res.put("data", data);
        return res;
    }

    // ==================================================================
    //  结构分布
    // ==================================================================

    /**
     * 绩效等级分布（S/A/B/C/D）
     */
    public List<Map<String, Object>> gradeDistribution(Integer deptId) {
        List<Map<String, Object>> raw = new ArrayList<>();
        long total = 0;
        for (Map<String, Object> r : efficiencyMapper.gradeDistribution(deptId)) {
            Map<String, Object> m = lowerRow(r);
            long cnt = lng(m, "cnt");
            total += cnt;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("grade", str(m, "grade"));
            row.put("count", cnt);
            raw.add(row);
        }
        // 固定按 S A B C D 排序，保证图例顺序稳定
        raw.sort(Comparator.comparing(r -> gradeOrder(str(r, "grade"))));
        for (Map<String, Object> row : raw) {
            row.put("ratio", rate(((Number) row.get("count")).doubleValue(), total));
        }
        return raw;
    }

    /**
     * 考勤状态构成
     */
    public List<Map<String, Object>> leaveTypeDistribution(Integer deptId) {
        List<Map<String, Object>> raw = new ArrayList<>();
        long total = 0;
        for (Map<String, Object> r : efficiencyMapper.leaveTypeDistribution(deptId)) {
            Map<String, Object> m = lowerRow(r);
            long cnt = lng(m, "cnt");
            total += cnt;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", str(m, "leave_type"));
            row.put("value", cnt);
            raw.add(row);
        }
        raw.sort((a, b) -> Long.compare((Long) b.get("value"), (Long) a.get("value")));
        for (Map<String, Object> row : raw) {
            row.put("ratio", rate(((Number) row.get("value")).doubleValue(), total));
        }
        return raw;
    }

    /**
     * 任务类型分布与完成质量
     */
    public List<Map<String, Object>> taskTypeDistribution(Integer deptId) {
        List<Map<String, Object>> res = new ArrayList<>();
        for (Map<String, Object> r : efficiencyMapper.taskTypeDistribution(deptId)) {
            Map<String, Object> m = lowerRow(r);
            double valid = dbl(m, "total_task") - dbl(m, "canceled_task");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("taskType", str(m, "task_type"));
            row.put("taskCount", lng(m, "total_task"));
            row.put("finishedCount", lng(m, "finished_task"));
            row.put("completionRate", rate(dbl(m, "finished_task"), valid));
            row.put("avgQuality", round2(dbl(m, "avg_quality") * 10));
            res.add(row);
        }
        return res;
    }

    // ==================================================================
    //  员工维度
    // ==================================================================

    /**
     * 员工效能榜单（按综合效能指数降序）
     */
    public List<Map<String, Object>> topEmployee(int limit, Integer deptId) {
        Map<Integer, String> deptNames = deptNames();
        Map<Integer, Map<String, Object>> rows = new LinkedHashMap<>();

        for (Map<String, Object> r : efficiencyMapper.empAttendance(deptId)) {
            Map<String, Object> m = lowerRow(r);
            int empId = (int) lng(m, "emp_id");
            Map<String, Object> row = rows.computeIfAbsent(empId, k -> new LinkedHashMap<>());
            row.put("empId", empId);
            row.put("empName", str(m, "emp_name"));
            int deptIdValue = (int) lng(m, "dept_id");
            row.put("deptId", deptIdValue);
            row.put("deptName", deptNames.getOrDefault(deptIdValue, "部门" + deptIdValue));
            double total = dbl(m, "total_days");
            double normal = dbl(m, "normal_days");
            double late = dbl(m, "late_days");
            row.put("attendanceRate", rate(normal, total));
            row.put("onTimeRate", rate(normal - late, normal));
            row.put("avgOvertime", dbl(m, "avg_overtime"));
        }

        for (Map<String, Object> r : efficiencyMapper.empTask(deptId)) {
            Map<String, Object> m = lowerRow(r);
            int empId = (int) lng(m, "emp_id");
            Map<String, Object> row = rows.get(empId);
            if (row == null) {
                continue;   // 无考勤记录的员工不参与排名
            }
            double valid = dbl(m, "total_task") - dbl(m, "canceled_task");
            row.put("taskCount", lng(m, "total_task"));
            row.put("completionRate", rate(dbl(m, "finished_task"), valid));
            row.put("avgQuality", round2(dbl(m, "avg_quality") * 10));
        }

        for (Map<String, Object> r : efficiencyMapper.empPerformance(deptId)) {
            Map<String, Object> m = lowerRow(r);
            int empId = (int) lng(m, "emp_id");
            Map<String, Object> row = rows.get(empId);
            if (row == null) {
                continue;
            }
            row.put("avgKpi", dbl(m, "avg_kpi"));
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : rows.values()) {
            if (!row.containsKey("avgKpi") || !row.containsKey("completionRate")) {
                continue;
            }
            row.put("score", score(row));
            list.add(row);
        }
        list.sort((a, b) -> Double.compare(dbl(b, "score"), dbl(a, "score")));
        for (int i = 0; i < list.size(); i++) {
            list.get(i).put("rank", i + 1);
        }
        return limit > 0 && list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
    }

    /**
     * 部门下拉列表
     */
    public List<Map<String, Object>> departments() {
        List<Map<String, Object>> res = new ArrayList<>();
        for (Department d : departmentMapper.selectList(null)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("deptId", d.getId());
            row.put("deptName", d.getName());
            res.add(row);
        }
        return res;
    }

    // ==================================================================
    //  私有工具方法
    // ==================================================================

    /** 综合效能指数：五项指标加权（全部百分制） */
    private double score(Map<String, Object> row) {
        double complete = dbl(row, "completionRate");
        double kpi = dbl(row, "avgKpi");
        double attend = dbl(row, "attendanceRate");
        double onTime = dbl(row, "onTimeRate");
        double quality = dbl(row, "avgQuality");
        double v = W_COMPLETE * complete + W_KPI * kpi + W_ATTEND * attend
                + W_ONTIME * onTime + W_QUALITY * quality;
        return round2(v);
    }

    private Map<String, Object> baseDeptRow(int deptId, Map<Integer, String> deptNames) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("deptId", deptId);
        row.put("deptName", deptNames.getOrDefault(deptId, "部门" + deptId));
        return row;
    }

    private Map<Integer, String> deptNames() {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (Department d : departmentMapper.selectList(null)) {
            map.put(d.getId(), d.getName());
        }
        return map;
    }

    private static int gradeOrder(String grade) {
        List<String> order = Arrays.asList("S", "A", "B", "C", "D");
        int i = order.indexOf(grade == null ? "" : grade.trim());
        return i < 0 ? 99 : i;
    }

    /** 百分率，分母为 0 时返回 0 */
    private static double rate(double numerator, double denominator) {
        return denominator <= 0 ? 0 : round2(numerator * 100.0 / denominator);
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** 把 Map 的 key 统一转成小写，屏蔽 MySQL / H2 列名大小写差异 */
    private static Map<String, Object> lowerRow(Map<String, Object> row) {
        if (row == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> res = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null) {
                res.put(e.getKey().toLowerCase(), e.getValue());
            }
        }
        return res;
    }

    private static double dbl(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : 0;
    }

    private static long lng(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }

    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? "" : String.valueOf(v);
    }
}
