package com.ems.bigdata.mr;

import com.ems.entity.MrJobHistory;
import com.ems.mapper.MrJobHistoryMapper;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.Counters;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.JobCounter;
import org.apache.hadoop.mapreduce.TaskCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MapReduce 作业调度服务
 *
 * 【它能做什么】
 * 把三个 Job 的提交过程封装成一次方法调用：自动清理输出目录、按配置组装
 * Configuration、提交作业、等待完成、采集 Counters 与采样输出，
 * 并把关键计数器写入 mr_job_history 表供大屏与性能实验读取。
 *
 * 【两种运行模式】
 *   framework = local（默认）
 *     由 LocalJobRunner 在当前 JVM 内完整执行 map → shuffle(spill/sort) → reduce。
 *     依然是货真价实的 MapReduce 框架流程（有 InputSplit、有排序、有 Combiner），
 *     只是不跨机器。无集群时用它验证逻辑与正确性。
 *   framework = yarn
 *     提交到 YARN，由 ResourceManager 分配 Container 在 nodemanager 上跑。
 *     论文中的"分布式并行加速比"实验必须在这个模式下做，
 *     并配合 8088 页面截图佐证。
 *
 * 【JDK 17 注意事项】
 * Hadoop 3.3.x 官方支持到 JDK 11。在 JDK 17 上运行时需为 Map/Reduce 容器
 * 追加 --add-opens 打开模块封装，本类已通过 mapreduce.*.java.opts 自动注入；
 * 驱动端（Spring Boot 进程）若报 InaccessibleObjectException，
 * 需在启动参数里加同样的 --add-opens，详见 docs 附录。
 */
@Service
public class MrJobService {

    private static final Logger log = LoggerFactory.getLogger(MrJobService.class);

    @Value("${bigdata.mr.framework:local}")
    private String framework;

    @Value("${bigdata.mr.fs-default-fs:file:///}")
    private String fsDefaultFs;

    @Value("${bigdata.mr.yarn-rm-hostname:localhost}")
    private String yarnRmHostname;

    @Value("${bigdata.mr.input-dir:./data/mr/input}")
    private String inputDir;

    @Value("${bigdata.mr.output-dir:./data/mr/output}")
    private String outputDir;

    @Value("${bigdata.mr.reduces:2}")
    private int reduces;

    @Value("${bigdata.mr.topn:10}")
    private int topN;

    /** 作业历史落库，required=false 保证没有该表时不影响作业本身 */
    @Autowired(required = false)
    private MrJobHistoryMapper jobHistoryMapper;

    /** JDK 17 下 Hadoop 反射访问 JDK 内部 API 所需 */
    private static final String JDK17_OPTS =
            "--add-opens java.base/java.lang=ALL-UNNAMED "
                    + "--add-opens java.base/java.util=ALL-UNNAMED "
                    + "-Djava.security.egd=file:/dev/./urandom";

    // ==================== 配置组装 ====================

    public Configuration buildConf() {
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", fsDefaultFs);
        conf.set("mapreduce.framework.name", framework);
        conf.set("mapreduce.app-submission.cross-platform", "true");
        conf.set("mapreduce.map.java.opts", JDK17_OPTS);
        conf.set("mapreduce.reduce.java.opts", JDK17_OPTS);
        // 本地模式下限制单节点资源占用，避免小数据集时启动过多 JVM
        conf.set("mapreduce.local.map.tasks.maximum", "4");
        if ("yarn".equalsIgnoreCase(framework)) {
            conf.set("yarn.resourcemanager.hostname", yarnRmHostname);
            conf.set("yarn.resourcemanager.address", yarnRmHostname + ":8032");
            conf.set("yarn.resourcemanager.scheduler.address", yarnRmHostname + ":8030");
            conf.set("yarn.resourcemanager.resource-tracker.address", yarnRmHostname + ":8031");
            conf.set("mapreduce.jobhistory.address", yarnRmHostname + ":10020");
        }
        return conf;
    }

    public Map<String, Object> getConfig() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("framework", framework);
        m.put("fs.defaultFS", fsDefaultFs);
        m.put("reduces", reduces);
        m.put("topN", topN);
        m.put("inputDir", inputDir);
        m.put("outputDir", outputDir);
        if ("yarn".equalsIgnoreCase(framework)) {
            m.put("yarn.resourcemanager.hostname", yarnRmHostname);
        }
        return m;
    }

    // ==================== 作业入口 ====================

    /** Job1：部门 × 月份效能聚合 */
    public Map<String, Object> runDeptEfficiency(Integer reducers) throws Exception {
        Configuration conf = buildConf();
        String in = join(inputDir, "attendance-dwd.txt");
        String out = join(outputDir, "dept-efficiency");
        Job job = DeptEfficiencyJob.buildJob(conf, in, out, reducers == null ? this.reduces : reducers);
        Map<String, Object> r = execute(job, out, "部门月度效能聚合");
        saveHistory(r);
        return r;
    }

    /** Job2：部门员工效能 TopN（二次排序） */
    public Map<String, Object> runDeptTopN(Integer n, Integer reducers) throws Exception {
        Configuration conf = buildConf();
        String in = join(inputDir, "efficiency-score.txt");
        String out = join(outputDir, "dept-topn");
        Job job = DeptTopNJob.buildJob(conf, in, out,
                reducers == null ? this.reduces : reducers, n == null ? this.topN : n);
        Map<String, Object> r = execute(job, out, "部门效能TopN(二次排序)");
        saveHistory(r);
        return r;
    }

    /**
     * Job3：维表关联
     *
     * @param mode "map" = Map-side Join（广播维表，无 Reduce）；"reduce" = Reduce-side Join
     */
    public Map<String, Object> runJoin(String mode, Integer reducers) throws Exception {
        Configuration conf = buildConf();
        String fact = join(inputDir, "performance-fact.txt");
        String dim = join(inputDir, "employee-dim.txt");
        boolean mapSide = !EmployeeDeptJoinJob.JOIN_REDUCE_SIDE.equalsIgnoreCase(mode);
        String out = join(outputDir, mapSide ? "join-map-side" : "join-reduce-side");
        Job job = mapSide
                ? EmployeeDeptJoinJob.buildMapSideJob(conf, fact, dim, out)
                : EmployeeDeptJoinJob.buildReduceSideJob(conf, fact, dim, out,
                reducers == null ? this.reduces : reducers);
        Map<String, Object> r = execute(job, out, mapSide ? "Map端Join" : "Reduce端Join");
        r.put("joinMode", mapSide ? "map-side" : "reduce-side");
        saveHistory(r);
        return r;
    }

    /** 一次性跑完全部作业，返回结果列表 */
    public List<Map<String, Object>> runAll() {
        List<Map<String, Object>> results = new ArrayList<>();
        results.add(safeRun("部门月度效能聚合", () -> runDeptEfficiency(null)));
        results.add(safeRun("部门效能TopN(二次排序)", () -> runDeptTopN(null, null)));
        results.add(safeRun("Map端Join", () -> runJoin("map", null)));
        results.add(safeRun("Reduce端Join", () -> runJoin("reduce", null)));
        return results;
    }

    private Map<String, Object> safeRun(String name, JobSupplier supplier) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jobName", name);
        try {
            Map<String, Object> r = supplier.get();
            m.putAll(r);
        } catch (Exception e) {
            m.put("success", false);
            m.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            saveHistory(m);
        }
        return m;
    }

    @FunctionalInterface
    private interface JobSupplier {
        Map<String, Object> get() throws Exception;
    }

    // ==================== 作业历史落库 ====================

    /**
     * 把作业执行结果写入 mr_job_history。
     *
     * 大屏的「计算引擎可观测」栏与 P4 性能对比实验都依赖这张表；
     * 这里吞掉所有异常——历史落库失败不应该让作业本身失败。
     */
    private void saveHistory(Map<String, Object> r) {
        if (jobHistoryMapper == null || r == null) {
            return;
        }
        try {
            MrJobHistory h = new MrJobHistory();
            h.setJobName(firstNonBlank(r.get("jobName"), r.get("job")));
            h.setJobId(str(r.get("jobId")));
            h.setJoinMode(str(r.get("joinMode")));
            h.setFramework(framework);
            h.setSuccess(Boolean.TRUE.equals(r.get("success")) ? 1 : 0);
            h.setDurationMs(num(r.get("durationMs")));
            h.setOutputPath(str(r.get("outputPath")));
            h.setErrorMsg(truncate(str(r.get("error")), 500));

            Object counters = r.get("counters");
            if (counters instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> c = (Map<String, Object>) counters;
                h.setMapInputRecords(num(c.get("MAP_INPUT_RECORDS")));
                h.setMapOutputRecords(num(c.get("MAP_OUTPUT_RECORDS")));
                h.setMapOutputBytes(num(c.get("MAP_OUTPUT_BYTES")));
                h.setCombineInputRecords(num(c.get("COMBINE_INPUT_RECORDS")));
                h.setCombineOutputRecords(num(c.get("COMBINE_OUTPUT_RECORDS")));
                h.setSpilledRecords(num(c.get("SPILLED_RECORDS")));
                h.setReduceInputGroups(num(c.get("REDUCE_INPUT_GROUPS")));
                h.setReduceInputRecords(num(c.get("REDUCE_INPUT_RECORDS")));
                h.setReduceOutputRecords(num(c.get("REDUCE_OUTPUT_RECORDS")));
                // local 模式下 LAUNCHED_* 恒为 0，回退到作业配置的任务数
                long maps = num(c.get("LAUNCHED_MAPS"));
                if (maps == 0) {
                    maps = num(r.get("mapTasks"));
                }
                // MultipleInputs 场景下分片数取不到，只要有输入记录就说明至少跑过一个 Map
                if (maps == 0 && num(c.get("MAP_INPUT_RECORDS")) > 0) {
                    maps = 1;
                }
                long reduces = num(c.get("LAUNCHED_REDUCES"));
                if (reduces == 0) {
                    reduces = num(r.get("reduceTasks"));
                }
                h.setLaunchedMaps((int) maps);
                h.setLaunchedReduces((int) reduces);
            }

            // DQC：有效记录数为总量，除「正常请假无签到」外的 BAD_* 与缺签到计为异常
            Object dqc = r.get("dqc");
            if (dqc instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> d = (Map<String, Object>) dqc;
                h.setDqcTotal(num(d.get("TOTAL_VALID_RECORDS")));
                long abnormal = num(d.get("BAD_FIELD_COUNT")) + num(d.get("BAD_NUMBER_FORMAT"))
                        + num(d.get("BAD_WORK_HOURS")) + num(d.get("BAD_OVERTIME"))
                        + num(d.get("MISSING_SIGN_IN"));
                h.setDqcAbnormal(abnormal);
            }

            jobHistoryMapper.insert(h);
        } catch (Exception e) {
            log.warn("MR 作业历史落库失败（不影响作业结果）: {}", e.getMessage());
        }
    }

    private static String firstNonBlank(Object a, Object b) {
        String s = str(a);
        return s == null || s.isEmpty() ? str(b) : s;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Long num(Object o) {
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

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ==================== 通用执行器 ====================

    private Map<String, Object> execute(Job job, String outPath, String jobName)
            throws IOException, InterruptedException, ClassNotFoundException {
        // 输出目录必须不存在，否则 Hadoop 直接抛异常——先清理
        deleteOutput(job.getConfiguration(), outPath);

        long start = System.currentTimeMillis();
        boolean ok = job.waitForCompletion(true);
        long cost = System.currentTimeMillis() - start;

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("jobName", jobName);
        r.put("success", ok);
        r.put("jobId", job.getJobID() == null ? "-" : job.getJobID().toString());
        r.put("jobNameInYarn", job.getJobName());
        r.put("framework", framework);
        r.put("durationMs", cost);
        r.put("outputPath", outPath);
        r.put("counters", collectCounters(job.getCounters()));
        // local 模式下 JobCounter 的 LAUNCHED_MAPS/REDUCES 常常不计入，
        // 这里补一份分片数与配置的 Reduce 数，保证大屏不会显示「Map 0」
        r.put("mapTasks", countSplits(job));
        r.put("reduceTasks", job.getNumReduceTasks());
        if (jobName.contains("聚合")) {
            r.put("dqc", DeptEfficiencyJob.extractDqcCounters(job.getCounters()));
        }
        if (ok) {
            try {
                r.put("sample", readOutput(outPath, 10));
            } catch (Exception e) {
                r.put("sampleError", e.getMessage());
            }
        }
        return r;
    }

    /** 输入分片数，即 Map 任务数；取不到时返回 0，不影响主流程 */
    private static int countSplits(Job job) {
        try {
            // Hadoop 3.x 中 getSplits 是实例方法，用 TextInputFormat 实例调用
            return new org.apache.hadoop.mapreduce.lib.input.TextInputFormat()
                    .getSplits(job).size();
        } catch (Exception e) {
            return 0;
        }
    }

    /** 采集关键计数器，用于论文中的性能对比表 */
    private Map<String, Object> collectCounters(Counters counters) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (counters == null) {
            return m;
        }
        putCounter(m, counters, "MAP_INPUT_RECORDS", TaskCounter.MAP_INPUT_RECORDS);
        putCounter(m, counters, "MAP_OUTPUT_RECORDS", TaskCounter.MAP_OUTPUT_RECORDS);
        putCounter(m, counters, "MAP_OUTPUT_BYTES", TaskCounter.MAP_OUTPUT_BYTES);
        putCounter(m, counters, "COMBINE_INPUT_RECORDS", TaskCounter.COMBINE_INPUT_RECORDS);
        putCounter(m, counters, "COMBINE_OUTPUT_RECORDS", TaskCounter.COMBINE_OUTPUT_RECORDS);
        putCounter(m, counters, "SPILLED_RECORDS", TaskCounter.SPILLED_RECORDS);
        putCounter(m, counters, "REDUCE_INPUT_GROUPS", TaskCounter.REDUCE_INPUT_GROUPS);
        putCounter(m, counters, "REDUCE_INPUT_RECORDS", TaskCounter.REDUCE_INPUT_RECORDS);
        putCounter(m, counters, "REDUCE_OUTPUT_RECORDS", TaskCounter.REDUCE_OUTPUT_RECORDS);
        putCounter(m, counters, "LAUNCHED_MAPS", JobCounter.TOTAL_LAUNCHED_MAPS);
        putCounter(m, counters, "LAUNCHED_REDUCES", JobCounter.TOTAL_LAUNCHED_REDUCES);
        putCounter(m, counters, "DATA_LOCAL_MAPS", JobCounter.DATA_LOCAL_MAPS);
        return m;
    }

    private void putCounter(Map<String, Object> m, Counters counters, String key, Enum<?> enumCounter) {
        Counter c = counters.findCounter(enumCounter);
        m.put(key, c == null ? 0 : c.getValue());
    }

    private void deleteOutput(Configuration conf, String outPath) throws IOException {
        try (FileSystem fs = FileSystem.get(conf)) {
            Path p = new Path(outPath);
            if (fs.exists(p)) {
                fs.delete(p, true);
            }
        }
    }

    /**
     * 采样读取作业输出，供前端直接展示，不必再手动 cat 文件。
     * 只读 part-* 开头的输出分片，跳过 _SUCCESS 等标识文件。
     */
    public List<String> readOutput(String outPath, int limit) throws IOException {
        List<String> lines = new ArrayList<>();
        try (FileSystem fs = FileSystem.get(buildConf())) {
            Path p = new Path(outPath);
            if (!fs.exists(p)) {
                return lines;
            }
            FileStatus[] files = fs.listStatus(p, new PathFilter() {
                @Override
                public boolean accept(Path path) {
                    return path.getName().startsWith("part-");
                }
            });
            for (FileStatus f : files) {
                if (lines.size() >= limit) {
                    break;
                }
                try (FSDataInputStream in = fs.open(f.getPath());
                     BufferedReader br = new BufferedReader(
                             new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null && lines.size() < limit) {
                        lines.add(line);
                    }
                }
            }
        }
        return lines;
    }

    private static String join(String base, String name) {
        if (base.endsWith("/")) {
            return base + name;
        }
        return base + "/" + name;
    }
}
