package com.ems.bigdata.mr;

import com.ems.entity.Attendance;
import com.ems.entity.Department;
import com.ems.entity.Employee;
import com.ems.entity.Performance;
import com.ems.mapper.AttendanceMapper;
import com.ems.mapper.DepartmentMapper;
import com.ems.mapper.EmployeeMapper;
import com.ems.mapper.PerformanceMapper;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MapReduce 输入导出器
 *
 * 【职责】
 * MapReduce 读的是 HDFS 上的文本文件，而业务数据在 MySQL。
 * 这一步等价于生产环境用 Sqoop / DataX 把关系库导入 Hadoop 的过程，
 * 本项目用轻量导出器代替，目的是让整条链路不依赖额外组件也能跑通。
 *
 * 【产出四个输入文件】
 *   1. attendance-dwd.txt   考勤事实（含退化维度 dept_id）  → Job1 输入
 *   2. efficiency-score.txt 员工月度效能分               → Job2 输入
 *   3. performance-fact.txt 绩效事实                     → Job3 事实表
 *   4. employee-dim.txt     员工维表                     → Job3 维表（广播）
 *
 * 【为什么用 Hadoop FileSystem API 而不是 java.io？】
 * 统一走 FileSystem 后，同一份代码既能在本地 file:/// 跑（无集群时验证逻辑），
 * 也能直接写入 hdfs://namenode:9000，切换只需改配置项 fs.defaultFS。
 */
@Component
public class MrInputExporter {

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private AttendanceMapper attendanceMapper;

    @Autowired
    private PerformanceMapper performanceMapper;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Value("${bigdata.mr.input-dir:./data/mr/input}")
    private String inputDir;

    @Value("${bigdata.mr.batch-size:5000}")
    private int batchSize;

    private Configuration hadoopConf;

    /** 懒加载 Hadoop 配置，避免无 Hadoop 环境时 Spring 容器启动失败 */
    private Configuration conf() {
        if (hadoopConf == null) {
            hadoopConf = new Configuration();
        }
        return hadoopConf;
    }

    private FileSystem fs() throws IOException {
        return FileSystem.get(conf());
    }

    /**
     * 导出全部 MR 输入文件
     *
     * @return 各文件名 → 记录数
     */
    public Map<String, Object> exportAll() throws IOException {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        FileSystem fs = fs();
        Path dir = new Path(inputDir);
        if (!fs.exists(dir)) {
            fs.mkdirs(dir);
        }

        // 一次性载入员工与部门（500 人 / 15 部门，规模可控）
        Map<Integer, Employee> empMap = new HashMap<>();
        for (Employee e : employeeMapper.selectList(null)) {
            empMap.put(e.getId(), e);
        }
        Map<Integer, String> deptMap = new HashMap<>();
        for (Department d : departmentMapper.selectList(null)) {
            deptMap.put(d.getId(), d.getName());
        }
        result.put("employees", empMap.size());
        result.put("departments", deptMap.size());

        // 孤儿记录计数：事实表中 emp_id 在员工表里不存在的记录（引用完整性缺失），
        // 这类记录没有部门归属，无法参与部门维度聚合，导出时跳过并单独报告
        long[] orphan = {0L};

        result.put("employee-dim.txt", exportDim(fs, dir, empMap, deptMap));
        result.put("attendance-dwd.txt", exportAttendance(fs, dir, empMap, orphan));
        result.put("performance-fact.txt", exportPerformance(fs, dir));
        result.put("efficiency-score.txt", exportScore(fs, dir, empMap, orphan));
        result.put("inputDir", dir.toString());
        if (orphan[0] > 0) {
            result.put("orphanSkipped", orphan[0]);
            result.put("orphanHint", "事实表中存在 emp_id 未匹配到员工的记录，已跳过（不参与部门聚合）");
        }
        return result;
    }

    /** 员工维表：emp_id \t name \t dept_id \t dept_name \t entry_date */
    private long exportDim(FileSystem fs, Path dir, Map<Integer, Employee> empMap,
                           Map<Integer, String> deptMap) throws IOException {
        Path p = new Path(dir, "employee-dim.txt");
        long n = 0;
        try (BufferedWriter w = writer(fs, p)) {
            w.write("# emp_id\tname\tdept_id\tdept_name\tentry_date");
            w.newLine();
            for (Employee e : empMap.values()) {
                int deptId = deptIdOf(e);
                String deptName = deptMap.getOrDefault(deptId, deptId == 0 ? "未分配部门" : "未知部门");
                w.write(String.format("%d\t%s\t%d\t%s\t%s",
                        e.getId(), safe(e.getName()), deptId, deptName,
                        e.getEntryDate() == null ? "" : e.getEntryDate()));
                w.newLine();
                n++;
            }
        }
        return n;
    }

    /** 考勤事实：emp_id \t dept_id \t work_date \t sign_in \t sign_out \t late_minutes \t overtime_hours \t leave_type \t work_hours */
    private long exportAttendance(FileSystem fs, Path dir, Map<Integer, Employee> empMap,
                                  long[] orphan) throws IOException {
        Path p = new Path(dir, "attendance-dwd.txt");
        long n = 0;
        long offset = 0;
        try (BufferedWriter w = writer(fs, p)) {
            while (true) {
                List<Attendance> batch = attendanceMapper.selectByPage(offset, batchSize);
                if (batch == null || batch.isEmpty()) {
                    break;
                }
                for (Attendance a : batch) {
                    Employee e = empMap.get(a.getEmpId());
                    if (e == null) {
                        orphan[0]++;
                        continue;
                    }
                    int deptId = deptIdOf(e);
                    w.write(String.format("%d\t%d\t%s\t%s\t%s\t%d\t%s\t%s\t%s",
                            a.getEmpId(), deptId,
                            a.getWorkDate() == null ? "" : a.getWorkDate(),
                            a.getSignInTime() == null ? "" : a.getSignInTime().toLocalTime(),
                            a.getSignOutTime() == null ? "" : a.getSignOutTime().toLocalTime(),
                            a.getLateMinutes() == null ? 0 : a.getLateMinutes(),
                            a.getOvertimeHours() == null ? "0.0" : a.getOvertimeHours().toPlainString(),
                            safe(a.getLeaveType()),
                            a.getWorkHours() == null ? "0.0" : a.getWorkHours().toPlainString()));
                    w.newLine();
                    n++;
                }
                offset += batchSize;
                if (batch.size() < batchSize) {
                    break;
                }
            }
        }
        return n;
    }

    /** 绩效事实：emp_id \t period \t kpi_score \t grade */
    private long exportPerformance(FileSystem fs, Path dir) throws IOException {
        Path p = new Path(dir, "performance-fact.txt");
        long n = 0;
        long offset = 0;
        try (BufferedWriter w = writer(fs, p)) {
            while (true) {
                List<Performance> batch = performanceMapper.selectByPage(offset, batchSize);
                if (batch == null || batch.isEmpty()) {
                    break;
                }
                for (Performance f : batch) {
                    w.write(String.format("%d\t%s\t%s\t%s",
                            f.getEmpId(), safe(f.getPeriod()),
                            f.getKpiScore() == null ? "0.0" : f.getKpiScore().toPlainString(),
                            safe(f.getGrade())));
                    w.newLine();
                    n++;
                }
                offset += batchSize;
                if (batch.size() < batchSize) {
                    break;
                }
            }
        }
        return n;
    }

    /** 效能分：emp_id \t dept_id \t period \t score */
    private long exportScore(FileSystem fs, Path dir, Map<Integer, Employee> empMap,
                             long[] orphan) throws IOException {
        Path p = new Path(dir, "efficiency-score.txt");
        long n = 0;
        long offset = 0;
        try (BufferedWriter w = writer(fs, p)) {
            while (true) {
                List<Performance> batch = performanceMapper.selectByPage(offset, batchSize);
                if (batch == null || batch.isEmpty()) {
                    break;
                }
                for (Performance f : batch) {
                    Employee e = empMap.get(f.getEmpId());
                    if (e == null) {
                        orphan[0]++;
                        continue;
                    }
                    int deptId = deptIdOf(e);
                    w.write(String.format("%d\t%d\t%s\t%s",
                            f.getEmpId(), deptId, safe(f.getPeriod()),
                            f.getKpiScore() == null ? "0.0" : f.getKpiScore().toPlainString()));
                    w.newLine();
                    n++;
                }
                offset += batchSize;
                if (batch.size() < batchSize) {
                    break;
                }
            }
        }
        return n;
    }

    /**
     * 取员工部门ID，兼容脏数据
     *
     * 业务库个别历史记录（如遗留测试数据）dept_id 为 NULL，
     * 直接拆箱会触发 NullPointerException 导致整批导出中断。
     * 这里统一映射到 0（未分配部门），保证链路不因单条脏数据失败——
     * 这本身也是数据质量处理（DQC）的一环。
     */
    private static int deptIdOf(Employee e) {
        return (e == null || e.getDeptId() == null) ? 0 : e.getDeptId();
    }

    private BufferedWriter writer(FileSystem fs, Path p) throws IOException {
        FSDataOutputStream out = fs.create(p, true);
        return new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace("\t", " ").trim();
    }
}
