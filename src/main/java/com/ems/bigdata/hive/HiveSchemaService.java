package com.ems.bigdata.hive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/**
 * Hive 数仓分层建模脚本编排服务
 *
 * 职责：把 resources/hive/ 下的 HiveQL 脚本按层加载并执行，完成：
 *   00~04  建库建表（ODS / DWD / DWS / ADS 四层 DDL）
 *   11     ODS → DWD 清洗转换（含拉链表处理）
 *   12     DWD → DWS 多维聚合（含 GROUPING SETS）
 *   13     DWS → ADS 排名与画像（含窗口函数）
 *
 * 脚本中的 ${dt} 会被替换为实际数据日期，实现按天重跑。
 */
@Service
public class HiveSchemaService {

    private static final Logger log = LoggerFactory.getLogger(HiveSchemaService.class);

    private static final String SCRIPT_LOCATION = "classpath*:hive/*.sql";

    @Autowired(required = false)
    private HiveRealService hiveRealService;

    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    /**
     * 列出所有可用的建模脚本
     */
    public List<String> listScripts() {
        List<String> names = new ArrayList<>();
        try {
            Resource[] resources = resourceResolver.getResources(SCRIPT_LOCATION);
            for (Resource resource : resources) {
                names.add(resource.getFilename());
            }
        } catch (IOException e) {
            log.error("[Hive建模] 扫描脚本失败", e);
        }
        Collections.sort(names);
        return names;
    }

    /**
     * 初始化四层表结构（执行 00~04 脚本）
     */
    public Map<String, Object> initSchema() {
        return runGroup("0", Collections.emptyMap(), "建库建表");
    }

    /**
     * ODS → DWD 清洗转换
     */
    public Map<String, Object> runOdsToDwd(String dt) {
        return runGroup("11", vars(dt), "ODS→DWD 清洗转换");
    }

    /**
     * DWD → DWS 多维聚合
     */
    public Map<String, Object> runDwdToDws(String dt) {
        return runGroup("12", vars(dt), "DWD→DWS 多维聚合");
    }

    /**
     * DWS → ADS 排名与画像
     */
    public Map<String, Object> runDwsToAds(String dt) {
        return runGroup("13", vars(dt), "DWS→ADS 排名画像");
    }

    /**
     * 执行完整建模链路：建表 → ODS→DWD → DWD→DWS → DWS→ADS
     *
     * 注意：本方法只执行脚本，不含 ODS 数据抽取。
     * 完整流程 = ETLPipeline.runOdsExtract() 抽取业务数据 → 本方法逐级建模。
     */
    public Map<String, Object> runFullPipeline(String dt) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("dt", dt);
        report.put("startTime", new Date());

        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(step("initSchema", initSchema()));
        steps.add(step("odsToDwd", runOdsToDwd(dt)));
        steps.add(step("dwdToDws", runDwdToDws(dt)));
        steps.add(step("dwsToAds", runDwsToAds(dt)));
        report.put("steps", steps);

        boolean allSuccess = steps.stream().allMatch(s -> "SUCCESS".equals(s.get("status")));
        report.put("status", allSuccess ? "SUCCESS" : "PARTIAL_FAILED");
        report.put("endTime", new Date());
        return report;
    }

    // ==================== 内部方法 ====================

    /** 按文件名前缀批量执行脚本 */
    private Map<String, Object> runGroup(String prefix, Map<String, String> vars, String description) {
        if (hiveRealService == null) {
            return error("HiveRealService 未加载，请确认 bigdata.mode=real 且 HiveServer2 可用");
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("stage", description);

        List<Resource> matched = new ArrayList<>();
        try {
            Resource[] resources = resourceResolver.getResources(SCRIPT_LOCATION);
            for (Resource resource : resources) {
                String name = resource.getFilename();
                if (name != null && name.startsWith(prefix) && name.endsWith(".sql")) {
                    matched.add(resource);
                }
            }
        } catch (IOException e) {
            log.error("[Hive建模] 扫描脚本失败", e);
            return error("脚本扫描失败: " + e.getMessage());
        }

        matched.sort(Comparator.comparing(r -> String.valueOf(r.getFilename())));
        if (matched.isEmpty()) {
            return error("未找到前缀为 " + prefix + " 的脚本");
        }

        List<Map<String, Object>> details = new ArrayList<>();
        int totalStatements = 0;
        int totalFailed = 0;

        for (Resource resource : matched) {
            String name = resource.getFilename();
            try {
                String script = readScript(resource);
                Map<String, Object> result = hiveRealService.executeScript(script, vars);
                totalStatements += (int) result.getOrDefault("totalStatements", 0);
                totalFailed += (int) result.getOrDefault("failedCount", 0);
                result.put("script", name);
                details.add(result);
                log.info("[Hive建模] {} 执行完成：{}/{} 条语句成功",
                        name, result.get("successCount"), result.get("totalStatements"));
            } catch (IOException e) {
                totalFailed++;
                Map<String, Object> fail = new LinkedHashMap<>();
                fail.put("script", name);
                fail.put("status", "READ_FAILED");
                fail.put("message", e.getMessage());
                details.add(fail);
            }
        }

        report.put("scripts", details);
        report.put("totalStatements", totalStatements);
        report.put("failedCount", totalFailed);
        report.put("costMs", details.stream().mapToLong(d -> ((Number) d.getOrDefault("costMs", 0)).longValue()).sum());
        report.put("status", totalFailed == 0 ? "SUCCESS" : "PARTIAL_FAILED");
        return report;
    }

    private Map<String, Object> step(String name, Map<String, Object> result) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("step", name);
        step.put("status", result.get("status"));
        step.put("costMs", result.get("costMs"));
        step.put("failedCount", result.get("failedCount"));
        step.put("detail", result);
        return step;
    }

    private String readScript(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Map<String, String> vars(String dt) {
        Map<String, String> vars = new HashMap<>();
        vars.put("dt", dt == null || dt.isEmpty() ? LocalDate.now().toString() : dt);
        return vars;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("status", "FAILED");
        report.put("message", message);
        return report;
    }
}
