package com.ems.controller;

import com.ems.bigdata.hive.HiveRealService;
import com.ems.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hive 配置与状态控制器
 *
 * 用于：
 * 1. 测试真实 Hive 连接是否正常
 * 2. 查看 Hive 表结构和状态
 * 3. 管理 Hive 运行模式切换
 */
@RestController
@RequestMapping("/api/hive")
public class HiveController {

    @Autowired(required = false)
    private HiveRealService hiveRealService;

    /**
     * 测试 Hive 连接状态
     * GET /api/hive/status
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        if (hiveRealService == null) {
            return Result.success(Map.of(
                    "connected", false,
                    "message", "HiveRealService 未加载——请设置 bigdata.mode=real 并确保 HiveServer2 在 " +
                              "localhost:10000 运行"
            ));
        }
        try {
            return Result.success(hiveRealService.checkConnection());
        } catch (Exception e) {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("connected", false);
            status.put("url", "jdbc:hive2://localhost:10000/default");
            status.put("message", "连接失败: " + e.getMessage());
            return Result.success(status);
        }
    }

    /**
     * 获取所有表
     * GET /api/hive/tables
     */
    @GetMapping("/tables")
    public Result<Object> showTables() {
        if (hiveRealService == null) {
            return Result.error("真实Hive服务未配置");
        }
        List<String> tables = hiveRealService.showTables();
        if (tables.isEmpty()) {
            return Result.success("Hive 中暂无表，请先执行 ETL", tables);
        }
        return Result.success(tables);
    }

    /**
     * 查看表结构
     * GET /api/hive/describe/{tableName}
     */
    @GetMapping("/describe/{tableName}")
    public Result<List<Map<String, String>>> describeTable(@PathVariable String tableName) {
        if (hiveRealService == null) {
            return Result.error("真实Hive服务未配置");
        }
        return Result.success(hiveRealService.describeTable(tableName));
    }
}
