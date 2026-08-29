package com.ems.bigdata.etl;

import com.ems.bigdata.hive.HiveRealService;
import com.ems.bigdata.hive.HiveSimulator;
import com.ems.entity.Employee;
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

    /** 运行模式：simulator | real */
    @Value("${bigdata.mode:simulator}")
    private String mode;

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
            // === 真实 Hive 模式 ===
            loadCount = hiveRealService.loadEmployeeData(transformedRows, partition);
            report.put("mode", "real");
            report.put("storage", "HDFS (Hive " + hiveRealService.toString() + ")");
        } else {
            // === 模拟模式 ===
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
