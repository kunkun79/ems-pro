package com.ems.controller;

import com.ems.bigdata.etl.ETLPipeline;
import com.ems.bigdata.hive.HiveRealService;
import com.ems.bigdata.hive.HiveSchemaService;
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

    @Autowired(required = false)
    private HiveSchemaService hiveSchemaService;

    @Autowired(required = false)
    private ETLPipeline etlPipeline;

    /** 当前大数据运行模式：simulator 不需要外部组件，real 才连接 HiveServer2 */
    @org.springframework.beans.factory.annotation.Value("${bigdata.mode:simulator}")
    private String mode;

    /**
     * 需要真实 Hive 的接口统一前置校验
     *
     * @return null 表示校验通过；非 null 为可直接返回给前端的错误提示
     */
    private String requireRealMode() {
        if (!"real".equalsIgnoreCase(mode)) {
            return "当前为模拟模式（bigdata.mode=" + mode + "），该接口需要真实 HiveServer2。"
                    + "请先执行 docker-compose up -d 启动集群，将 application.yml 中 bigdata.mode 改为 real 后重启应用。";
        }
        if (hiveRealService == null && hiveSchemaService == null) {
            return "Hive 服务未加载，请确认 bigdata.mode=real 且 HiveServer2 可访问";
        }
        return null;
    }

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
        String err = requireRealMode();
        if (err != null) {
            return Result.error(err);
        }
        try {
            List<String> tables = hiveRealService.showTables();
            if (tables.isEmpty()) {
                return Result.success("Hive 中暂无表，请先执行 ETL", tables);
            }
            return Result.success(tables);
        } catch (Exception e) {
            return Result.error("查询 Hive 表失败：" + e.getMessage());
        }
    }

    /**
     * 查看表结构
     * GET /api/hive/describe/{tableName}
     */
    @GetMapping("/describe/{tableName}")
    public Result<List<Map<String, String>>> describeTable(@PathVariable String tableName) {
        String err = requireRealMode();
        if (err != null) {
            return Result.error(err);
        }
        try {
            return Result.success(hiveRealService.describeTable(tableName));
        } catch (Exception e) {
            return Result.error("查看表结构失败：" + e.getMessage());
        }
    }

    // ========== 以下为 P2 阶段新增：数仓分层建模接口 ==========

    /**
     * 查看可用的建模脚本
     * GET /api/hive/scripts
     */
    @GetMapping("/scripts")
    public Result<List<String>> listScripts() {
        if (hiveSchemaService == null) {
            return Result.error("建模服务未加载");
        }
        return Result.success(hiveSchemaService.listScripts());
    }

    /**
     * 初始化四层表结构
     * POST /api/hive/schema/init
     */
    @PostMapping("/schema/init")
    public Result<Map<String, Object>> initSchema() {
        String err = requireRealMode();
        if (err != null) {
            return Result.error(err);
        }
        try {
            return Result.success("建表完成", hiveSchemaService.initSchema());
        } catch (Exception e) {
            return Result.error("建表失败：" + e.getMessage());
        }
    }

    /**
     * 抽取业务表数据到 ODS 层
     * POST /api/hive/etl/ods?limit=0&dt=2026-10-05
     * limit=0 表示全量抽取
     */
    @PostMapping("/etl/ods")
    public Result<Map<String, Object>> extractToOds(
            @RequestParam(defaultValue = "0") int limit,
            @RequestParam(required = false) String dt) {
        if (etlPipeline == null) {
            return Result.error("ETL 管道未加载");
        }
        return Result.success("ODS 抽取完成", etlPipeline.runOdsExtract(limit, dt));
    }

    /**
     * 执行单层转换
     * POST /api/hive/model/ods-to-dwd?dt=2026-10-05
     */
    @PostMapping("/model/{stage}")
    public Result<Map<String, Object>> runStage(
            @PathVariable String stage,
            @RequestParam(required = false) String dt) {
        if (hiveSchemaService == null) {
            return Result.error("建模服务未加载，请确认 bigdata.mode=real");
        }
        String err = requireRealMode();
        if (err != null) {
            return Result.error(err);
        }
        Map<String, Object> report;
        try {
            switch (stage) {
                case "ods-to-dwd" -> report = hiveSchemaService.runOdsToDwd(dt);
                case "dwd-to-dws" -> report = hiveSchemaService.runDwdToDws(dt);
                case "dws-to-ads" -> report = hiveSchemaService.runDwsToAds(dt);
                default -> {
                    return Result.error("未知的建模阶段: " + stage
                            + "（可选 ods-to-dwd / dwd-to-dws / dws-to-ads）");
                }
            }
        } catch (Exception e) {
            return Result.error(stage + " 执行失败：" + e.getMessage());
        }
        return Result.success(stage + " 执行完成", report);
    }

    /**
     * 一键执行完整建模链路：建表 → ODS→DWD → DWD→DWS → DWS→ADS
     * POST /api/hive/model/full?dt=2026-10-05
     */
    @PostMapping("/model/full")
    public Result<Map<String, Object>> runFullPipeline(@RequestParam(required = false) String dt) {
        String err = requireRealMode();
        if (err != null) {
            return Result.error(err);
        }
        try {
            return Result.success("建模链路执行完成", hiveSchemaService.runFullPipeline(dt));
        } catch (Exception e) {
            return Result.error("建模链路执行失败：" + e.getMessage());
        }
    }

    /**
     * 通用 HiveQL 查询（调试用）
     * GET /api/hive/query?sql=SELECT COUNT(*) FROM ods_attendance
     */
    @GetMapping("/query")
    public Result<List<Map<String, Object>>> query(@RequestParam String sql) {
        String err = requireRealMode();
        if (err != null) {
            return Result.error(err);
        }
        try {
            return Result.success(hiveRealService.query(sql, null));
        } catch (Exception e) {
            return Result.error("查询失败：" + e.getMessage());
        }
    }
}
