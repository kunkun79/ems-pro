package com.ems.service;

import java.util.List;
import java.util.Map;

/**
 * 数据分析服务（大数据模块核心接口）
 *
 * 【学习要点——大数据在业务中的体现】
 * 这个接口整合了操作日志分析、员工画像等分析功能。
 * 在真实的大数据架构中，这些数据会存入Hive数仓，
 * 通过Spark/Flink进行分析，这里我们先在关系数据库中实现，
 * 后续可平滑迁移到大数据平台。
 */
public interface AnalysisService {

    /** 操作类型分布（饼图数据） */
    List<Map<String, Object>> operationTypeDistribution();

    /** 每日操作趋势（折线图数据） */
    List<Map<String, Object>> dailyOperationTrend();

    /** 每小时操作热力（热力图数据） */
    List<Map<String, Object>> hourlyOperationHeatmap();

    /** 员工画像分析 */
    Map<String, Object> employeePortrait();

    /** 部门效能分析 */
    List<Map<String, Object>> departmentEfficiency();
}
