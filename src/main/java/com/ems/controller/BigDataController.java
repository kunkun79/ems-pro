package com.ems.controller;

import com.ems.bigdata.analysis.MapReduceDemo;
import com.ems.bigdata.etl.ETLPipeline;
import com.ems.bigdata.hive.HiveSimulator;
import com.ems.common.Result;
import com.ems.entity.Employee;
import com.ems.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 大数据模块控制器
 *
 * 【学习要点】
 * 这个Controller展示了如何在传统Web应用中集成大数据功能。
 * 实际项目中，这些大数据操作通常通过调度系统（如Azkaban/Airflow）定时执行。
 */
@RestController
@RequestMapping("/api/bigdata")
public class BigDataController {

    @Autowired
    private HiveSimulator hive;

    @Autowired
    private ETLPipeline etl;

    @Autowired
    private EmployeeService employeeService;

    // ========== ETL管道操作 ==========

    /** 执行ETL：将业务数据导入Hive数仓 */
    @PostMapping("/etl/run")
    public Result<Map<String, Object>> runETL() {
        return Result.success("ETL执行完成", etl.runEmployeeETL());
    }

    /** 查询Hive数仓中的数据 */
    @GetMapping("/hive/query")
    public Result<List<Map<String, Object>>> queryHive() {
        return Result.success(etl.queryFromHive());
    }

    /** 查看Hive表结构 */
    @GetMapping("/hive/tables")
    public Result<Map<String, Object>> showHiveTables() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("tables", hive.showTables());
        for (String table : hive.showTables()) {
            info.put(table + "_columns", hive.describeTable(table));
        }
        return Result.success(info);
    }

    /** 按部门汇总（Hive GROUP BY） */
    @GetMapping("/hive/department-summary")
    public Result<Map<String, Long>> departmentSummary() {
        return Result.success(etl.departmentSummary());
    }

    // ========== MapReduce 演示 ==========

    /** WordCount 演示 */
    @PostMapping("/mapreduce/wordcount")
    public Result<Map<String, Integer>> wordCount(@RequestBody Map<String, String> params) {
        String text = params.getOrDefault("text", "Hello World Hello Hadoop Hello Hive");
        List<String> lines = Arrays.asList(text.split("\n"));
        return Result.success(MapReduceDemo.wordCount(lines));
    }

    /** 按部门薪资汇总（MapReduce Demo） */
    @GetMapping("/mapreduce/salary-by-dept")
    public Result<Map<String, Double>> salaryByDept() {
        // 通过 Service 层获取员工数据（遵循三层架构）
        List<Map<String, Object>> employees = employeeService.pageWithDept(1, 9999, null)
                .getRecords().stream().map(emp -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("dept", emp.getDeptId());
                    map.put("salary", emp.getSalary() != null ? emp.getSalary().doubleValue() : 0);
                    return map;
                }).collect(Collectors.toList());

        return Result.success(MapReduceDemo.salarySumByDept(employees));
    }

    /** 按部门平均年龄（MapReduce Demo） */
    @GetMapping("/mapreduce/avg-age-by-dept")
    public Result<Map<String, Double>> avgAgeByDept() {
        // 通过 Service 层获取员工数据（遵循三层架构）
        List<Map<String, Object>> employees = employeeService.pageWithDept(1, 9999, null)
                .getRecords().stream().map(emp -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("dept", emp.getDeptId());
                    map.put("age", emp.getAge() != null ? emp.getAge() : 0);
                    return map;
                }).collect(Collectors.toList());

        return Result.success(MapReduceDemo.avgAgeByDept(employees));
    }

    /** 获取大数据模块状态 */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("hiveTables", hive.showTables().size());
        status.put("etlAvailable", true);
        status.put("mapReduceAvailable", true);
        status.put("etlMode", etl.getMode());
        status.put("message", "real".equals(etl.getMode())
                ? "大数据模块运行正常（真实 Hive 模式）"
                : "大数据模块运行正常（模拟模式）");
        return Result.success(status);
    }
}
