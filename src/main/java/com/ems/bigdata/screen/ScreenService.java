package com.ems.bigdata.screen;

import com.ems.bigdata.hive.HiveRealService;
import com.ems.entity.MrJobHistory;
import com.ems.mapper.AttendanceMapper;
import com.ems.mapper.DepartmentMapper;
import com.ems.mapper.EmployeeMapper;
import com.ems.mapper.MrJobHistoryMapper;
import com.ems.mapper.PerformanceMapper;
import com.ems.mapper.TaskMapper;
import com.ems.service.EfficiencyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大数据可视化大屏数据服务
 *
 * 【设计出发点】
 * 大屏要证明「大数据系统真的在跑」，而不是把普通业务指标画好看。
 * 因此数据分三块：
 *   1. 数据链路：数仓四层各层的行数与分区 → 证明分层建模真的落地了
 *   2. 计算引擎：MapReduce 作业的 Counters（Shuffle 规模、任务数、耗时）→ 证明分布式计算真的执行了
 *   3. 集群资源：HDFS 容量与 YARN 资源 → 证明作业跑在真实集群上
 *
 * 【两种模式下的数据来源】
 *   mode = real：分层存量走 HiveServer2 真实 count(*)，集群指标走 NameNode / ResourceManager 的 REST 接口
 *   mode = simulator：Hive 只有 ODS 层能对应到源库行数，DWD 及其以上显示「未建模」；
 *                     集群指标显示「未连接」，并提示需要启动集群后切换到 real
 */
@Service
public class ScreenService {

    /** 数仓四层表清单：层名 + 表名 + 展示名 */
    private static final String[][] LAYER_TABLES = {
            {"ODS", "ods_employee", "员工"},
            {"ODS", "ods_department", "部门"},
            {"ODS", "ods_attendance", "考勤"},
            {"ODS", "ods_task", "任务"},
            {"ODS", "ods_performance", "绩效"},
            {"DWD", "dwd_dim_employee", "员工维表(拉链)"},
            {"DWD", "dwd_fact_attendance_day", "考勤日明细"},
            {"DWD", "dwd_fact_task_detail", "任务明细"},
            {"DWD", "dwd_fact_performance_month", "绩效月明细"},
            {"DWS", "dws_emp_efficiency_month", "员工效能月表"},
            {"DWS", "dws_dept_efficiency_month", "部门效能月表"},
            {"ADS", "ads_emp_efficiency_rank", "员工效能排名"},
            {"ADS", "ads_dept_efficiency_profile", "部门效能画像"}
    };

    @Value("${bigdata.mode:simulator}")
    private String mode;

    @Value("${bigdata.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    @Value("${bigdata.cluster.namenode-ui:http://localhost:9870}")
    private String namenodeUi;

    @Value("${bigdata.cluster.rm-ui:http://localhost:8088}")
    private String rmUi;

    @Autowired
    private MrJobHistoryMapper jobHistoryMapper;

    @Autowired
    private HiveRealService hiveRealService;

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private AttendanceMapper attendanceMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private PerformanceMapper performanceMapper;

    @Autowired
    private EfficiencyService efficiencyService;

    private final ObjectMapper json = new ObjectMapper();

    /** 分层存量缓存：real 模式下每张表 count(*) 都是一个 Hive 作业，60 秒内复用结果 */
    private volatile Map<String, Object> layerCache;
    private volatile long layerCacheAt = 0;

    private boolean isReal() {
        return "real".equalsIgnoreCase(mode);
    }

    // ==================== 1. 数仓分层存量 ====================

    public Map<String, Object> layers() {
        long now = System.currentTimeMillis();
        if (layerCache != null && now - layerCacheAt < 60_000) {
            return layerCache;
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("mode", mode);
        r.put("source", isReal() ? "hive" : "mysql");

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Long> layerTotal = new LinkedHashMap<>();
        layerTotal.put("ODS", 0L);
        layerTotal.put("DWD", 0L);
        layerTotal.put("DWS", 0L);
        layerTotal.put("ADS", 0L);

        for (String[] t : LAYER_TABLES) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("layer", t[0]);
            item.put("table", t[1]);
            item.put("label", t[2]);
            Long cnt = isReal() ? hiveCount(t[1]) : mysqlCount(t[1]);
            item.put("rows", cnt);
            item.put("available", cnt != null);
            items.add(item);
            if (cnt != null) {
                layerTotal.merge(t[0], cnt, Long::sum);
            }
        }
        r.put("tables", items);

        List<Map<String, Object>> summary = new ArrayList<>();
        for (Map.Entry<String, Long> e : layerTotal.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("layer", e.getKey());
            m.put("rows", e.getValue());
            summary.add(m);
        }
        r.put("layers", summary);
        r.put("queryAt", now);

        layerCache = r;
        layerCacheAt = now;
        return r;
    }

    /** Hive 真实行数查询，失败返回 null（表未建模时很常见） */
    private Long hiveCount(String table) {
        try {
            List<Map<String, Object>> rows =
                    hiveRealService.query("SELECT COUNT(1) AS cnt FROM " + table, null);
            if (rows == null || rows.isEmpty()) {
                return null;
            }
            Map<String, Object> lower = lowerKeys(rows.get(0));
            Object v = lower.get("cnt");
            return v == null ? null : Long.parseLong(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    /** 模拟模式下用源库行数代替 ODS 层存量 */
    private Long mysqlCount(String table) {
        try {
            switch (table) {
                case "ods_employee":
                    return employeeMapper.selectCount(null);
                case "ods_department":
                    return departmentMapper.selectCount(null);
                case "ods_attendance":
                    return attendanceMapper.selectCount(null);
                case "ods_task":
                    return taskMapper.selectCount(null);
                case "ods_performance":
                    return performanceMapper.selectCount(null);
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 2. ETL 管道状态 ====================

    public Map<String, Object> etl() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("mode", mode);
        r.put("schedulerEnabled", schedulerEnabled);
        r.put("cron", "0 0 2 * * ?（每日 02:00）");
        r.put("batchSize", 2000);

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("employee", employeeMapper.selectCount(null));
        source.put("department", departmentMapper.selectCount(null));
        source.put("attendance", attendanceMapper.selectCount(null));
        source.put("task", taskMapper.selectCount(null));
        source.put("performance", performanceMapper.selectCount(null));
        r.put("sourceRows", source);
        return r;
    }

    // ==================== 3. DQC 数据质量 ====================

    public Map<String, Object> dqc() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("available", false);
        if (jobHistoryMapper == null) {
            return r;
        }
        List<MrJobHistory> recent = jobHistoryMapper.selectRecent(50);
        for (MrJobHistory h : recent) {
            Long total = h.getDqcTotal();
            if (total != null && total > 0) {
                r.put("available", true);
                r.put("jobName", h.getJobName());
                r.put("total", total);
                r.put("abnormal", h.getDqcAbnormal() == null ? 0 : h.getDqcAbnormal());
                r.put("rate", total == 0 ? 0
                        : (h.getDqcAbnormal() == null ? 0 : h.getDqcAbnormal()) * 100.0 / total);
                r.put("at", h.getCreateTime() == null ? null : h.getCreateTime().toString());
                return r;
            }
        }
        r.put("hint", "尚未执行带 DQC 的作业，请在 MapReduce 页面运行「部门效能聚合」");
        return r;
    }

    // ==================== 4. MapReduce 作业 ====================

    public Map<String, Object> jobs() {
        Map<String, Object> r = new LinkedHashMap<>();
        if (jobHistoryMapper == null) {
            r.put("available", false);
            return r;
        }
        List<Map<String, Object>> latest = new ArrayList<>();
        for (MrJobHistory h : jobHistoryMapper.selectLatestPerJob()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("jobName", h.getJobName());
            m.put("jobId", h.getJobId());
            m.put("joinMode", h.getJoinMode());
            m.put("framework", h.getFramework());
            m.put("success", h.getSuccess() != null && h.getSuccess() == 1);
            m.put("durationMs", h.getDurationMs());
            m.put("mapInputRecords", nvl(h.getMapInputRecords()));
            m.put("mapOutputRecords", nvl(h.getMapOutputRecords()));
            m.put("mapOutputBytes", nvl(h.getMapOutputBytes()));
            m.put("combineInputRecords", nvl(h.getCombineInputRecords()));
            m.put("combineOutputRecords", nvl(h.getCombineOutputRecords()));
            m.put("spilledRecords", nvl(h.getSpilledRecords()));
            m.put("reduceInputGroups", nvl(h.getReduceInputGroups()));
            m.put("reduceOutputRecords", nvl(h.getReduceOutputRecords()));
            m.put("launchedMaps", nvl(h.getLaunchedMaps()));
            m.put("launchedReduces", nvl(h.getLaunchedReduces()));
            m.put("at", h.getCreateTime() == null ? null : h.getCreateTime().toString());
            latest.add(m);
        }
        r.put("latest", latest);
        r.put("available", !latest.isEmpty());

        Map<String, Object> sum = lowerKeys(jobHistoryMapper.summary());
        // 统一成驼峰，避免 MySQL 返回 snake/全小写、H2 返回大写时前端取不到值
        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("total", longOf(sum.get("total")));
        stat.put("successCount", longOf(sum.get("successcount")));
        stat.put("shuffleBytes", longOf(sum.get("shufflebytes")));
        stat.put("inputRecords", longOf(sum.get("inputrecords")));
        stat.put("totalDuration", longOf(sum.get("totalduration")));
        r.put("summary", stat);

        // P4 加速比实验数据：按 Reduce 数分组的平均耗时
        List<Map<String, Object>> scaling = new ArrayList<>();
        for (Map<String, Object> row : jobHistoryMapper.groupByReduces("部门月度效能聚合")) {
            Map<String, Object> m = lowerKeys(row);
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("reduces", m.get("reduces"));
            o.put("runs", m.get("runs"));
            o.put("avgDuration", m.get("avgduration"));
            o.put("avgShuffle", m.get("avgshuffle"));
            scaling.add(o);
        }
        r.put("reduceScaling", scaling);
        return r;
    }

    // ==================== 5. 集群资源 ====================

    public Map<String, Object> cluster() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("mode", mode);
        r.put("connected", false);
        r.put("namenodeUi", namenodeUi);
        r.put("rmUi", rmUi);

        Map<String, Object> hdfs = new LinkedHashMap<>();
        Map<String, Object> yarn = new LinkedHashMap<>();
        boolean hdfsOk = false;
        boolean yarnOk = false;

        try {
            String body = httpGet(namenodeUi
                    + "/jmx?qry=Hadoop:service=NameNode,name=FSNamesystemState");
            JsonNode bean = json.readTree(body).path("beans").path(0);
            if (!bean.isMissingNode()) {
                long total = bean.path("CapacityTotal").asLong(0);
                long used = bean.path("CapacityUsed").asLong(0);
                hdfs.put("capacityTotal", total);
                hdfs.put("capacityUsed", used);
                hdfs.put("capacityRemaining", bean.path("CapacityRemaining").asLong(0));
                hdfs.put("usedPercent", total == 0 ? 0 : used * 100.0 / total);
                hdfs.put("liveNodes", bean.path("NumLiveDataNodes").asInt(0));
                hdfs.put("deadNodes", bean.path("NumDeadDataNodes").asInt(0));
                hdfs.put("totalFiles", bean.path("TotalFiles").asLong(0));
                hdfs.put("totalBlocks", bean.path("TotalBlocks").asLong(0));
                hdfsOk = true;
            }
        } catch (Exception e) {
            hdfs.put("error", "无法连接 NameNode（" + e.getMessage() + "）");
        }
        r.put("hdfs", hdfs);

        try {
            String body = httpGet(rmUi + "/ws/v1/cluster/metrics");
            JsonNode m = json.readTree(body).path("clusterMetrics");
            if (!m.isMissingNode()) {
                yarn.put("activeNodes", m.path("activeNodes").asInt(0));
                yarn.put("appsRunning", m.path("appsRunning").asInt(0));
                yarn.put("appsCompleted", m.path("appsCompleted").asInt(0));
                yarn.put("appsFailed", m.path("appsFailed").asInt(0));
                yarn.put("containersAllocated", m.path("allocatedContainers").asInt(0));
                yarn.put("totalMB", m.path("totalMB").asLong(0));
                yarn.put("availableMB", m.path("availableMB").asLong(0));
                yarn.put("totalVirtualCores", m.path("totalVirtualCores").asInt(0));
                long totalMB = m.path("totalMB").asLong(0);
                long availMB = m.path("availableMB").asLong(0);
                yarn.put("memoryUsedPercent", totalMB == 0 ? 0 : (totalMB - availMB) * 100.0 / totalMB);
                yarnOk = true;
            }
        } catch (Exception e) {
            yarn.put("error", "无法连接 ResourceManager（" + e.getMessage() + "）");
        }
        r.put("yarn", yarn);
        r.put("connected", hdfsOk || yarnOk);
        if (!hdfsOk && !yarnOk) {
            r.put("hint", "未连接集群：请先 docker-compose up -d 启动 Hadoop，"
                    + "并在 application.yml 中将 bigdata.mode 设为 real");
        }
        return r;
    }

    private String httpGet(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    // ==================== 6. 大屏总览 ====================

    public Map<String, Object> overview() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("layers", layers());
        r.put("etl", etl());
        r.put("dqc", dqc());
        r.put("jobs", jobs());
        try {
            r.put("efficiencyOverview", efficiencyService.overview(null));
            r.put("deptRank", efficiencyService.deptRank(15));
            r.put("trend", efficiencyService.trend(null));
        } catch (Exception e) {
            r.put("efficiencyError", e.getMessage());
        }
        return r;
    }

    // ==================== 工具方法 ====================

    /** MySQL 原样返回别名、H2 返回大写，这里统一成小写再取值 */
    private static Map<String, Object> lowerKeys(Map<String, Object> src) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (src == null) {
            return m;
        }
        for (Map.Entry<String, Object> e : src.entrySet()) {
            m.put(e.getKey() == null ? null : e.getKey().toLowerCase(), e.getValue());
        }
        return m;
    }

    private static long nvl(Number n) {
        return n == null ? 0L : n.longValue();
    }

    private static long longOf(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
