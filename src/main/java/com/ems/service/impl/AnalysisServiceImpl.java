package com.ems.service.impl;

import com.ems.mapper.OperationLogMapper;
import com.ems.service.AnalysisService;
import com.ems.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 数据分析服务实现
 *
 * 【学习要点——大数据分析的本质】
 * 这个类的统计查询如果在百万/千万级数据上跑，关系数据库会非常慢。
 * 在大数据架构中，这些数据会：
 * 1. 通过ETL管道导入 Hive 数据仓库
 * 2. 用 Spark SQL 或 HiveQL 进行聚合计算
 * 3. 结果存入 MySQL/Redis 供前端展示
 *
 * 本项目中我们在 bigdata 包中模拟了这个流程。
 */
@Service
public class AnalysisServiceImpl implements AnalysisService {

    @Autowired
    private OperationLogMapper logMapper;

    @Autowired
    private EmployeeService employeeService;

    @Override
    public List<Map<String, Object>> operationTypeDistribution() {
        return logMapper.countByAction();
    }

    @Override
    public List<Map<String, Object>> dailyOperationTrend() {
        return logMapper.countByDate();
    }

    @Override
    public List<Map<String, Object>> hourlyOperationHeatmap() {
        return logMapper.countByHour();
    }

    @Override
    public Map<String, Object> employeePortrait() {
        Map<String, Object> portrait = new LinkedHashMap<>();
        portrait.put("genderDistribution", employeeService.countByGender());
        portrait.put("salaryDistribution", employeeService.countBySalaryRange());
        portrait.put("deptDistribution", employeeService.countByDept());
        portrait.put("analysisTime", new Date());
        return portrait;
    }

    @Override
    public List<Map<String, Object>> departmentEfficiency() {
        // 简单模拟：计算每个部门的员工数量
        return employeeService.countByDept();
    }
}
