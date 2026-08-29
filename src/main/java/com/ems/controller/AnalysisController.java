package com.ems.controller;

import com.ems.common.Result;
import com.ems.service.AnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 数据分析控制器（大数据大屏的API）
 */
@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    @Autowired
    private AnalysisService analysisService;

    /** 操作类型分布 */
    @GetMapping("/operation/distribution")
    public Result<List<Map<String, Object>>> operationDistribution() {
        return Result.success(analysisService.operationTypeDistribution());
    }

    /** 每日操作趋势 */
    @GetMapping("/operation/trend")
    public Result<List<Map<String, Object>>> operationTrend() {
        return Result.success(analysisService.dailyOperationTrend());
    }

    /** 每小时操作热力 */
    @GetMapping("/operation/heatmap")
    public Result<List<Map<String, Object>>> operationHeatmap() {
        return Result.success(analysisService.hourlyOperationHeatmap());
    }

    /** 员工画像 */
    @GetMapping("/employee/portrait")
    public Result<Map<String, Object>> employeePortrait() {
        return Result.success(analysisService.employeePortrait());
    }

    /** 部门效能 */
    @GetMapping("/department/efficiency")
    public Result<List<Map<String, Object>>> departmentEfficiency() {
        return Result.success(analysisService.departmentEfficiency());
    }
}
