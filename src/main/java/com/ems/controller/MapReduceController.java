package com.ems.controller;

import com.ems.bigdata.mr.MrInputExporter;
import com.ems.bigdata.mr.MrJobService;
import com.ems.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MapReduce 作业控制器
 *
 * 【与 /api/bigdata/mapreduce/* 的区别】
 * 旧接口（MapReduceDemo）是单机集合运算的教学演示，用来讲清 Map/Shuffle/Reduce 思想；
 * 本控制器下的是真实 Hadoop MapReduce 作业，会走完整的
 * InputSplit → Map → Spill/Sort → Shuffle → Reduce → OutputFormat 流程，
 * 可在 YARN 8088 页面查到 job 记录。
 *
 * 调用顺序：
 *   1. POST /api/mapreduce/export          业务库 → MR 输入文件
 *   2. POST /api/mapreduce/job/all（或单独调用各 job）
 *   3. GET  /api/mapreduce/output           查看结果
 */
@RestController
@RequestMapping("/api/mapreduce")
public class MapReduceController {

    @Autowired
    private MrJobService mrJobService;

    @Autowired
    private MrInputExporter mrInputExporter;

    /** 查看当前 MR 运行配置（框架模式、reduce 数、输入输出路径） */
    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        return Result.success(mrJobService.getConfig());
    }

    /** 导出 MR 输入数据 */
    @PostMapping("/export")
    public Result<Map<String, Object>> export() {
        try {
            return Result.success("输入数据导出完成", mrInputExporter.exportAll());
        } catch (Exception e) {
            return Result.error("导出失败：" + e.getMessage());
        }
    }

    /** Job1：部门 × 月份效能聚合（Combiner + 自定义 Partitioner） */
    @PostMapping("/job/efficiency")
    public Result<Map<String, Object>> efficiency(@RequestParam(required = false) Integer reduces) {
        try {
            return Result.success(mrJobService.runDeptEfficiency(reduces));
        } catch (Exception e) {
            return Result.error("Job1 执行失败：" + e.getMessage());
        }
    }

    /** Job2：部门效能 TopN（二次排序） */
    @PostMapping("/job/topn")
    public Result<Map<String, Object>> topN(@RequestParam(required = false) Integer n,
                                            @RequestParam(required = false) Integer reduces) {
        try {
            return Result.success(mrJobService.runDeptTopN(n, reduces));
        } catch (Exception e) {
            return Result.error("Job2 执行失败：" + e.getMessage());
        }
    }

    /**
     * Job3：维表关联
     *
     * @param mode map = Map 端广播 Join（无 Shuffle）；reduce = Reduce 端 Join
     */
    @PostMapping("/job/join")
    public Result<Map<String, Object>> join(@RequestParam(defaultValue = "map") String mode,
                                            @RequestParam(required = false) Integer reduces) {
        try {
            return Result.success(mrJobService.runJoin(mode, reduces));
        } catch (Exception e) {
            return Result.error("Job3 执行失败：" + e.getMessage());
        }
    }

    /** 一键运行全部作业 */
    @PostMapping("/job/all")
    public Result<List<Map<String, Object>>> runAll() {
        return Result.success(mrJobService.runAll());
    }

    /** 采样查看作业输出 */
    @GetMapping("/output")
    public Result<List<String>> output(@RequestParam String path,
                                       @RequestParam(defaultValue = "20") int limit) {
        try {
            return Result.success(mrJobService.readOutput(path, limit));
        } catch (Exception e) {
            return Result.error("读取输出失败：" + e.getMessage());
        }
    }
}
