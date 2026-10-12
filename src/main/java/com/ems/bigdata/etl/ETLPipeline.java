package com.ems.bigdata.etl;

import com.ems.bigdata.hive.HiveRealService;
import com.ems.bigdata.hive.HiveSimulator;
import com.ems.entity.*;
import com.ems.mapper.AttendanceMapper;
import com.ems.mapper.DepartmentMapper;
import com.ems.mapper.PerformanceMapper;
import com.ems.mapper.TaskMapper;
import com.ems.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ETL 数据管道（双模式）
 *
 * 支持两种运行模式，通过配置 bigdata.mode 切换：
 * - simulator：使用 HiveSimulator（本地文件模拟，无需外部环境）
 * - real：使用 HiveRealService（通过 JDBC 连接真实 HiveServer2）
 *
 * P2 阶段新增 ODS 层抽取能力：把五张业务表原样搬进数仓 ODS 层，
 * 为后续 DWD/DWS/ADS 分层建模提供数据基础。
 *
 * 典型的大数据ETL工具：
 * - Apache Sqoop：关系数据库 ↔ Hadoop
 * - Apache Flume：日志收集
 * - Apache Spark：实时/批量数据处理
 * - Apache Kafka：消息队列/流处理
 */
@Component
public class ETLPipeline {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private HiveSimulator hiveSimulator;

    @Autowired(required = false)
    private HiveRealService hiveRealService;

    @Autowired
    private AttendanceMapper attendanceMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private PerformanceMapper performanceMapper;

    @Autowired
    private DepartmentMapper departmentMapper;

    /** 运行模式：simulator | real */
    @Value("${bigdata.mode:simulator}")
    private String mode;

    /** 每批读取并写入的行数 */
    @Value("${bigdata.etl.batch-size:2000}")
    private int batchSize;

    // ==================== ODS 层抽取（P2 新增） ====================

    /**
     * 抽取全部业务表到 ODS 层
     *
     * @param limit 每张事实表的最大抽取条数，0 表示全量
     * @param dt    数据日期分区，为空则取当天
     */
    public Map<String, Object> runOdsExtract(int limit, String dt) {
        String partition = (dt == null || dt.isEmpty())
                ? LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) : dt;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("mode", mode);
        report.put("partition", partition);
        report.put("startTime", new Date());
        report.put("limit", limit == 0 ? "全量" : limit);

        List<Map<String, Object>> tables = new ArrayList<>();

        tables.add(extractEmployee(partition));
        tables.add(extractDepartment(partition));
        tables.add(extractAttendance(partition, limit));
        tables.add(extractTask(partition, limit));
        tables.add(extractPerformance(partition, limit));

        report.put("tables", tables);
        report.put("totalRows", tables.stream()
                .mapToLong(t -> ((Number) t.getOrDefault("loaded", 0)).longValue()).sum());
        report.put("endTime", new Date());
        report.put("status", tables.stream().allMatch(t -> "SUCCESS".equals(t.get("status")))
                ? "SUCCESS" : "PARTIAL_FAILED");
        return report;
    }

    /** 员工快照 → ods_employee */
    private Map<String, Object> extractEmployee(String partition) {
        List<String> columns = Arrays.asList("emp_id", "name", "gender", "age",
                "dept_id", "salary", "entry_date", "status");
        List<Employee> employees = employeeService.pageWithDept(1, 100000, null).getRecords();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Employee emp : employees) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("emp_id", emp.getId());
            row.put("name", emp.getName());
            row.put("gender", emp.getGender());
            row.put("age", emp.getAge());
            row.put("dept_id", emp.getDeptId());
            row.put("salary", emp.getSalary());
            row.put("entry_date", str(emp.getEntryDate()));
            row.put("status", emp.getStatus());
            rows.add(row);
        }
        return load("ods_employee", columns, rows, partition);
    }

    /** 部门 → ods_department */
    private Map<String, Object> extractDepartment(String partition) {
        List<String> columns = Arrays.asList("dept_id", "dept_name", "location", "head_count");
        List<Department> departments = departmentMapper.selectList(null);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Department dept : departments) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dept_id", dept.getId());
            row.put("dept_name", dept.getName());
            row.put("location", dept.getLocation());
            row.put("head_count", dept.getHeadCount());
            rows.add(row);
        }
        return load("ods_department", columns, rows, partition);
    }

    /** 考勤事实 → ods_attendance（数据量主体，按批读取避免 OOM） */
    private Map<String, Object> extractAttendance(String partition, int limit) {
        List<String> columns = Arrays.asList("emp_id", "work_date", "sign_in_time", "sign_out_time",
                "late_minutes", "overtime_hours", "leave_type", "work_hours");

        long total = attendanceMapper.selectCount(null);
        long target = limit > 0 ? Math.min(limit, total) : total;

        List<Map<String, Object>> rows = new ArrayList<>();
        long offset = 0;
        while (offset < target) {
            int size = (int) Math.min(batchSize, target - offset);
            List<Attendance> batch = attendanceMapper.selectByPage(offset, size);
            if (batch.isEmpty()) {
                break;
            }
            for (Attendance a : batch) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("emp_id", a.getEmpId());
                row.put("work_date", str(a.getWorkDate()));
                row.put("sign_in_time", str(a.getSignInTime()));
                row.put("sign_out_time", str(a.getSignOutTime()));
                row.put("late_minutes", a.getLateMinutes());
                row.put("overtime_hours", a.getOvertimeHours());
                row.put("leave_type", a.getLeaveType());
                row.put("work_hours", a.getWorkHours());
                rows.add(row);
            }
            offset += batch.size();
        }
        Map<String, Object> result = load("ods_attendance", columns, rows, partition);
        result.put("sourceTotal", total);
        return result;
    }

    /** 任务工单 → ods_task */
    private Map<String, Object> extractTask(String partition, int limit) {
        List<String> columns = Arrays.asList("emp_id", "task_code", "task_type", "difficulty",
                "assign_date", "due_date", "finish_date", "status", "quality_score");

        long total = taskMapper.selectCount(null);
        long target = limit > 0 ? Math.min(limit, total) : total;

        List<Map<String, Object>> rows = new ArrayList<>();
        long offset = 0;
        while (offset < target) {
            int size = (int) Math.min(batchSize, target - offset);
            List<Task> batch = taskMapper.selectByPage(offset, size);
            if (batch.isEmpty()) {
                break;
            }
            for (Task t : batch) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("emp_id", t.getEmpId());
                row.put("task_code", t.getTaskCode());
                row.put("task_type", t.getTaskType());
                row.put("difficulty", t.getDifficulty());
                row.put("assign_date", str(t.getAssignDate()));
                row.put("due_date", str(t.getDueDate()));
                row.put("finish_date", str(t.getFinishDate()));
                row.put("status", t.getStatus());
                row.put("quality_score", t.getQualityScore());
                rows.add(row);
            }
            offset += batch.size();
        }
        Map<String, Object> result = load("ods_task", columns, rows, partition);
        result.put("sourceTotal", total);
        return result;
    }

    /** 绩效考核 → ods_performance */
    private Map<String, Object> extractPerformance(String partition, int limit) {
        List<String> columns = Arrays.asList("emp_id", "period", "kpi_score", "grade");

        long total = performanceMapper.selectCount(null);
        long target = limit > 0 ? Math.min(limit, total) : total;

        List<Map<String, Object>> rows = new ArrayList<>();
        long offset = 0;
        while (offset < target) {
            int size = (int) Math.min(batchSize, target - offset);
            List<Performance> batch = performanceMapper.selectByPage(offset, size);
            if (batch.isEmpty()) {
                break;
            }
            for (Performance p : batch) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("emp_id", p.getEmpId());
                row.put("period", p.getPeriod());
                row.put("kpi_score", p.getKpiScore());
                row.put("grade", p.getGrade());
                rows.add(row);
            }
            offset += batch.size();
        }
        Map<String, Object> result = load("ods_performance", columns, rows, partition);
        result.put("sourceTotal", total);
        return result;
    }

    /**
     * 统一落库：真实 Hive 走 JDBC 批处理，模拟模式写本地文件
     */
    private Map<String, Object> load(String table, List<String> columns,
                                     List<Map<String, Object>> rows, String partition) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("table", table);
        result.put("rows", rows.size());

        long start = System.currentTimeMillis();
        int loaded;
        if ("real".equalsIgnoreCase(mode) && hiveRealService != null) {
            loaded = hiveRealService.loadRows(table, columns, toValues(columns, rows), partition, batchSize);
            result.put("storage", "Hive / HDFS");
        } else {
            hiveSimulator.createTable(table, columns, "dt");
            hiveSimulator.insert(table, rows, partition);
            loaded = rows.size();
            result.put("storage", "本地文件模拟 (data/hive/warehouse/)");
        }
        result.put("loaded", loaded);
        result.put("costMs", System.currentTimeMillis() - start);
        result.put("status", loaded == rows.size() ? "SUCCESS" : "PARTIAL");
        return result;
    }

    /** 把 Map 行转换为按列顺序排列的值列表 */
    private List<List<Object>> toValues(List<String> columns, List<Map<String, Object>> rows) {
        List<List<Object>> values = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            List<Object> line = new ArrayList<>(columns.size());
            for (String col : columns) {
                line.add(row.get(col));
            }
            values.add(line);
        }
        return values;
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    // ==================== 原有 ETL 能力（保留） ====================

    /**
     * 执行完整的ETL流程：从业务数据库 → 数据仓库
     */
    public Map<String, Object> runEmployeeETL() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("startTime", new Date());
        report.put("pipeline", "员工数据ETL");

        // ====== Phase 1: EXTRACT ======
        List<Employee> employees = employeeService.pageWithDept(1, 9999, null).getRecords();
        report.put("extractCount", employees.size());

        // ====== Phase 2: TRANSFORM ======
        List<Map<String, Object>> transformedRows = new ArrayList<>();
        int cleanedCount = 0;
        for (Employee emp : employees) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", emp.getId());
            row.put("name", emp.getName());

            String gender = emp.getGender();
            if (gender == null || gender.isEmpty()) {
                gender = "未知"; cleanedCount++;
            }
            row.put("gender", gender);

            String phone = emp.getPhone();
            if (phone != null && phone.length() >= 7) {
                phone = phone.substring(0, 3) + "****" + phone.substring(7);
                cleanedCount++;
            }
            row.put("phone", phone);

            row.put("age", emp.getAge());
            row.put("salary", emp.getSalary());
            row.put("status", emp.getStatus());

            if (emp.getEntryDate() != null) {
                row.put("work_years", LocalDate.now().getYear() - emp.getEntryDate().getYear());
            } else {
                row.put("work_years", 0);
            }
            transformedRows.add(row);
        }
        report.put("transformCount", transformedRows.size());
        report.put("cleanedFields", cleanedCount);

        // ====== Phase 3: LOAD ======
        String partition = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        int loadCount = 0;

        if ("real".equalsIgnoreCase(mode) && hiveRealService != null) {
            loadCount = hiveRealService.loadEmployeeData(transformedRows, partition);
            report.put("mode", "real");
            report.put("storage", "HDFS (Hive " + hiveRealService.toString() + ")");
        } else {
            List<String> columns = Arrays.asList("id", "name", "gender", "phone", "age",
                    "salary", "status", "work_years");
            hiveSimulator.createTable("dim_employee", columns, "dt");
            hiveSimulator.insert("dim_employee", transformedRows, partition);
            loadCount = transformedRows.size();
            report.put("mode", "simulator");
            report.put("storage", "本地文件系统 (data/hive/warehouse/)");
        }

        report.put("loadPartition", partition);
        report.put("loadCount", loadCount);
        report.put("status", "SUCCESS");
        report.put("endTime", new Date());
        return report;
    }

    /** 查询 Hive 数仓中的数据 */
    public List<Map<String, Object>> queryFromHive() {
        if ("real".equalsIgnoreCase(mode) && hiveRealService != null) {
            return hiveRealService.queryAll("dim_employee");
        }
        return hiveSimulator.selectAll("dim_employee");
    }

    /** 按部门汇总（GROUP BY） */
    public Map<String, Long> departmentSummary() {
        if ("real".equalsIgnoreCase(mode) && hiveRealService != null) {
            return hiveRealService.groupByCount("dim_employee", "status");
        }
        return hiveSimulator.groupByCount("dim_employee", "dept_id");
    }

    /** 获取当前运行模式 */
    public String getMode() {
        return mode;
    }
}
