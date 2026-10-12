package com.ems.bigdata.mr;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Job 3：员工维表 × 效能事实表 关联
 *
 * 【为什么单独做一个 Join Job？】
 * 事实表里只有 emp_id，前端看板要显示"姓名 / 部门 / 司龄"。
 * 关联是数仓最常见的算子，也是面试与答辩的高频考点。
 * 本 Job 同时实现了两种主流方案，可在论文里做对比实验：
 *
 * ┌──────────────┬────────────────────────┬─────────────────────────┐
 * │              │ Map-side Join          │ Reduce-side Join        │
 * ├──────────────┼────────────────────────┼─────────────────────────┤
 * │ 原理         │ 小维表广播到每个 Map，  │ 两表都打标签 Shuffle，   │
 * │              │ map 阶段直接补全       │ 按 key 汇聚后关联       │
 * │ Shuffle      │ 无（reduce 数设为 0）  │ 全量事实 + 维表都要传   │
 * │ 适用         │ 维表能装进 Map 内存    │ 两表都很大              │
 * │ 本项目       │ 500 人维表 ≈ 30KB ✅   │ 通用兜底方案            │
 * └──────────────┴────────────────────────┴─────────────────────────┘
 *
 * 【输入格式】
 *   事实表: emp_id \t period \t kpi_score \t grade
 *   维表:   emp_id \t name \t dept_id \t dept_name \t entry_date
 * 【输出格式】emp_id \t period \t kpi_score \t grade \t name \t dept_id \t dept_name
 */
public class EmployeeDeptJoinJob {

    public static final String JOIN_MAP_SIDE = "map";
    public static final String JOIN_REDUCE_SIDE = "reduce";

    // ==================== 共用：维表解析 ====================

    /** 维表缓存，key = emp_id */
    static class DimTable {
        final Map<Integer, String> rows = new HashMap<>();
        int size() {
            return rows.size();
        }
        String get(int empId) {
            return rows.get(empId);
        }
    }

    private static DimTable loadDim(Configuration conf, URI cacheUri) throws IOException {
        DimTable dim = new DimTable();
        Path p = new Path(cacheUri);
        try (FileSystem fs = FileSystem.get(conf);
             BufferedReader br = new BufferedReader(
                     new InputStreamReader(fs.open(p), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split("\t");
                if (f.length >= 5) {
                    // 只保留 join 需要的字段：name, dept_id, dept_name, entry_date
                    dim.rows.put(Integer.parseInt(f[0].trim()),
                            f[1] + "\t" + f[2] + "\t" + f[3] + "\t" + f[4]);
                }
            }
        }
        return dim;
    }

    // ==================== 方案 A：Map-side Join ====================

    /**
     * Map 端 Join（又称 Broadcast Join / Map Join）
     *
     * 关键点：
     *   1. setup() 中通过 job.addCacheFile 分发的维表读进内存 HashMap
     *   2. map() 里 O(1) 查表补全维度，输出即为最终结果
     *   3. job.setNumReduceTasks(0) —— 没有 Reduce 阶段，自然没有 Shuffle
     *
     * 注意：若维表太大装不进 Map Task 内存，必须改用 Reduce-side Join 或
     * 先用"小表筛选 + 大表分桶"的策略，否则会 OOM。
     */
    public static class MapJoinMapper extends Mapper<LongWritable, Text, Text, Text> {

        private DimTable dim;
        private final Text outKey = new Text();
        private final Text outValue = new Text();

        @Override
        protected void setup(Context context) throws IOException {
            URI[] caches = context.getCacheFiles();
            if (caches == null || caches.length == 0) {
                throw new IOException("未找到分布式缓存中的维表文件，请检查 job.addCacheFile 配置");
            }
            Configuration conf = context.getConfiguration();
            dim = loadDim(conf, caches[0]);
            context.getCounter("MAPJOIN", "DIM_ROWS").increment(dim.size());
        }

        @Override
        protected void map(LongWritable key, Text value, Context context)
                throws IOException, InterruptedException {
            String line = value.toString().trim();
            if (line.isEmpty() || line.startsWith("#")) {
                return;
            }
            String[] f = line.split("\t");
            if (f.length < 3) {
                context.getCounter("DQC", "BAD_FIELD_COUNT").increment(1);
                return;
            }
            int empId;
            try {
                empId = Integer.parseInt(f[0].trim());
            } catch (NumberFormatException e) {
                context.getCounter("DQC", "BAD_NUMBER_FORMAT").increment(1);
                return;
            }
            String dimRow = dim.get(empId);
            if (dimRow == null) {
                // 事实表存在而维表缺失：典型的维表迟到问题（late-arriving dimension）
                context.getCounter("DQC", "DIM_MISSING").increment(1);
                return;
            }
            outKey.set(f[0].trim() + "\t" + f[1].trim());
            outValue.set(f[2].trim() + "\t" + (f.length > 3 ? f[3].trim() : "") + "\t" + dimRow);
            context.write(outKey, outValue);
            context.getCounter("MAPJOIN", "JOINED_ROWS").increment(1);
        }
    }

    public static Job buildMapSideJob(Configuration conf, String factPath, String dimPath,
                                      String outputPath) throws IOException {
        conf.setIfUnset("mapreduce.output.textoutputformat.separator", "\t");
        Job job = Job.getInstance(conf, "EMS-MapSideJoin");
        job.setJarByClass(EmployeeDeptJoinJob.class);

        // 维表广播：Hadoop 会把该文件复制到每个 Map Task 的工作目录
        job.addCacheFile(new Path(dimPath).toUri());

        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        TextInputFormat.addInputPath(job, new Path(factPath));
        FileOutputFormat.setOutputPath(job, new Path(outputPath));

        job.setMapperClass(MapJoinMapper.class);
        // 没有 Reduce：map 的输出直接落盘，这是 map-side join 性能优势的来源
        job.setNumReduceTasks(0);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        return job;
    }

    // ==================== 方案 B：Reduce-side Join ====================

    /** 事实表 Mapper：打 F 标签 */
    public static class FactMapper extends Mapper<LongWritable, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outValue = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context)
                throws IOException, InterruptedException {
            String line = value.toString().trim();
            if (line.isEmpty() || line.startsWith("#")) {
                return;
            }
            String[] f = line.split("\t");
            if (f.length < 3) {
                return;
            }
            outKey.set(f[0].trim());
            outValue.set("F\t" + f[1].trim() + "\t" + f[2].trim() + "\t" + (f.length > 3 ? f[3].trim() : ""));
            context.write(outKey, outValue);
            context.getCounter("REDUCEJOIN", "FACT_ROWS").increment(1);
        }
    }

    /** 维表 Mapper：打 D 标签 */
    public static class DimMapper extends Mapper<LongWritable, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outValue = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context)
                throws IOException, InterruptedException {
            String line = value.toString().trim();
            if (line.isEmpty() || line.startsWith("#")) {
                return;
            }
            String[] f = line.split("\t");
            if (f.length < 5) {
                return;
            }
            outKey.set(f[0].trim());
            outValue.set("D\t" + f[1] + "\t" + f[2] + "\t" + f[3] + "\t" + f[4]);
            context.write(outKey, outValue);
            context.getCounter("REDUCEJOIN", "DIM_ROWS").increment(1);
        }
    }

    /**
     * Reduce 端 Join
     *
     * 同一 emp_id 的两类记录被 Shuffle 到一起，按标签拆分后做笛卡尔积。
     * 因为维表是 1:1（一个员工一条），实际就是"取唯一的维表记录 + 逐条事实拼接"。
     * 若维表是一对多，这里会产生数据膨胀，需要在业务上先去重——这是
     * Reduce-side Join 最容易踩的坑，论文里值得写一笔。
     */
    public static class JoinReducer extends Reducer<Text, Text, Text, Text> {

        private final Text outKey = new Text();
        private final Text outValue = new Text();

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            String dimRow = null;
            List<String> facts = new ArrayList<>();
            for (Text v : values) {
                String s = v.toString();
                int tab = s.indexOf('\t');
                String tag = s.substring(0, tab);
                String body = s.substring(tab + 1);
                if ("D".equals(tag)) {
                    dimRow = body;
                } else {
                    facts.add(body);
                }
            }
            if (dimRow == null) {
                context.getCounter("DQC", "DIM_MISSING").increment(1);
                return;
            }
            for (String fact : facts) {
                String[] p = fact.split("\t");
                outKey.set(key + "\t" + (p.length > 0 ? p[0] : ""));
                outValue.set((p.length > 1 ? p[1] : "") + "\t" + (p.length > 2 ? p[2] : "") + "\t" + dimRow);
                context.write(outKey, outValue);
                context.getCounter("REDUCEJOIN", "JOINED_ROWS").increment(1);
            }
        }
    }

    public static Job buildReduceSideJob(Configuration conf, String factPath, String dimPath,
                                         String outputPath, int reducers) throws IOException {
        conf.setIfUnset("mapreduce.output.textoutputformat.separator", "\t");
        Job job = Job.getInstance(conf, "EMS-ReduceSideJoin");
        job.setJarByClass(EmployeeDeptJoinJob.class);

        job.setOutputFormatClass(TextOutputFormat.class);
        // MultipleInputs：同一作业接入两种格式的输入源，各自用不同 Mapper
        MultipleInputs.addInputPath(job, new Path(factPath), TextInputFormat.class, FactMapper.class);
        MultipleInputs.addInputPath(job, new Path(dimPath), TextInputFormat.class, DimMapper.class);
        FileOutputFormat.setOutputPath(job, new Path(outputPath));

        job.setReducerClass(JoinReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        job.setNumReduceTasks(Math.max(1, reducers));
        return job;
    }
}
