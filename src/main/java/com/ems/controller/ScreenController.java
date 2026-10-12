package com.ems.controller;

import com.ems.bigdata.screen.ScreenService;
import com.ems.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 大数据可视化大屏接口
 *
 * 与 /api/efficiency/* 的区别：
 *   /api/efficiency/* 面向「业务分析」，回答的是「员工效能怎么样」
 *   /api/screen/*     面向「系统可观测」，回答的是「大数据系统跑得怎么样」
 *
 * 后者包括数仓分层存量、ETL 管道状态、DQC 数据质量、
 * MapReduce 作业历史与 Counters、HDFS/YARN 集群资源——
 * 这些都是论文截图与答辩演示时的直接证据。
 */
@RestController
@RequestMapping("/api/screen")
public class ScreenController {

    @Autowired
    private ScreenService screenService;

    /** 大屏总览：一次拿到链路、管道、质量、作业、业务五类数据 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.success(screenService.overview());
    }

    /** 数仓四层存量（60 秒缓存，避免每个表都触发一次 Hive 作业） */
    @GetMapping("/layers")
    public Result<Map<String, Object>> layers() {
        return Result.success(screenService.layers());
    }

    /** ETL 管道状态：模式、调度、源库待抽取行数 */
    @GetMapping("/etl")
    public Result<Map<String, Object>> etl() {
        return Result.success(screenService.etl());
    }

    /** DQC 数据质量：取自 MR 作业 Counters */
    @GetMapping("/dqc")
    public Result<Map<String, Object>> dqc() {
        return Result.success(screenService.dqc());
    }

    /** MapReduce 作业历史与 Counters */
    @GetMapping("/jobs")
    public Result<Map<String, Object>> jobs() {
        return Result.success(screenService.jobs());
    }

    /** 集群资源：HDFS 与 YARN，未连接时给出明确提示 */
    @GetMapping("/cluster")
    public Result<Map<String, Object>> cluster() {
        return Result.success(screenService.cluster());
    }
}
