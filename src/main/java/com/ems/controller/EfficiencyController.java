package com.ems.controller;

import com.ems.common.Result;
import com.ems.service.EfficiencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 员工效能可视化接口
 *
 * 数据来源是 P1 阶段生成的一年期业务数据（考勤 / 任务 / 绩效三张事实表），
 * 与 Hive 数仓 ODS 层同源，因此本页展示的口径与 DWS 效能宽表一致。
 *
 * 所有接口都接受可选的 deptId：不传统计全公司，传入则只看该部门。
 */
@RestController
@RequestMapping("/api/efficiency")
public class EfficiencyController {

    @Autowired
    private EfficiencyService efficiencyService;

    /** 部门列表（页面筛选下拉用） */
    @GetMapping("/departments")
    public Result<List<Map<String, Object>>> departments() {
        return Result.success(efficiencyService.departments());
    }

    /** 总览卡片 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(@RequestParam(required = false) Integer deptId) {
        return Result.success(efficiencyService.overview(deptId));
    }

    /** 月度效能趋势 */
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(required = false) Integer deptId) {
        return Result.success(efficiencyService.trend(deptId));
    }

    /** 部门效能排行 */
    @GetMapping("/dept-rank")
    public Result<List<Map<String, Object>>> deptRank(@RequestParam(defaultValue = "15") int limit) {
        return Result.success(efficiencyService.deptRank(limit));
    }

    /** 部门 × 月份 出勤率热力图 */
    @GetMapping("/heatmap")
    public Result<Map<String, Object>> heatmap() {
        return Result.success(efficiencyService.heatmap());
    }

    /** 绩效等级分布 */
    @GetMapping("/grade")
    public Result<List<Map<String, Object>>> grade(@RequestParam(required = false) Integer deptId) {
        return Result.success(efficiencyService.gradeDistribution(deptId));
    }

    /** 考勤状态构成 */
    @GetMapping("/leave-type")
    public Result<List<Map<String, Object>>> leaveType(@RequestParam(required = false) Integer deptId) {
        return Result.success(efficiencyService.leaveTypeDistribution(deptId));
    }

    /** 任务类型分布 */
    @GetMapping("/task-type")
    public Result<List<Map<String, Object>>> taskType(@RequestParam(required = false) Integer deptId) {
        return Result.success(efficiencyService.taskTypeDistribution(deptId));
    }

    /** 员工效能榜 */
    @GetMapping("/top-employee")
    public Result<List<Map<String, Object>>> topEmployee(@RequestParam(defaultValue = "10") int limit,
                                                          @RequestParam(required = false) Integer deptId) {
        return Result.success(efficiencyService.topEmployee(limit, deptId));
    }
}
