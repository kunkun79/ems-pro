package com.ems.bigdata.mr;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableComparator;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Job 2：部门员工效能 TopN —— 二次排序（Secondary Sort）实现
 *
 * 【业务目标】
 * 每个部门取"历史最佳月度效能"排名前 N 的员工，用于人才识别与绩效激励。
 *
 * 【为什么必须用二次排序？】
 * 直观写法：key = deptId，value = (empId, score)，在 Reducer 里把所有员工装进 List 排序。
 * 问题是部门规模一大（本项目 500 人 × 12 个月），List 常驻内存容易 OOM，
 * 而且 Reducer 必须等全部数据到齐才能输出，无法提前终止。
 *
 * 二次排序把排序下推到 Shuffle：
 *   key = (deptId, score)，排序规则"部门升序 + 分数降序"
 *   → Reducer 拿到的 values 已经是有序的，取前 N 个即可 break
 *   → 内存占用恒定 O(N)，与部门人数无关
 *
 * 【三个必须配套配置的组件】
 *   job.setPartitionerClass(...)        按部门分桶
 *   job.setSortComparatorClass(...)     部门升序 + 分数降序
 *   job.setGroupingComparatorClass(...) 只按部门分组
 * 少配任何一个，结果都会错：不配分组比较器会导致每个 key 触发一次 reduce()；
 * 不配排序比较器则分数顺序不确定。
 *
 * 【输入格式】emp_id \t dept_id \t period \t score
 * 【输出格式】key: dept_id   value: rank \t emp_id \t period \t score
 */
public class DeptTopNJob {

    /** 配置项：每个部门取前几名 */
    public static final String CONF_TOP_N = "ems.mr.topn.n";

    // ==================== Mapper ====================

    public static class TopNMapper extends Mapper<LongWritable, Text, ScoreKey, Text> {

        private final ScoreKey outKey = new ScoreKey();
        private final Text outValue = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context)
                throws IOException, InterruptedException {
            String line = value.toString().trim();
            if (line.isEmpty() || line.startsWith("#")) {
                return;
            }
            String[] f = line.split("\t");
            if (f.length < 4) {
                context.getCounter("DQC", "BAD_FIELD_COUNT").increment(1);
                return;
            }
            try {
                int empId = Integer.parseInt(f[0].trim());
                int deptId = Integer.parseInt(f[1].trim());
                double score = Double.parseDouble(f[3].trim());
                if (score < 0 || score > 100) {
                    context.getCounter("DQC", "BAD_SCORE_RANGE").increment(1);
                    return;
                }
                outKey.set(deptId, score);
                outValue.set(empId + "\t" + f[2].trim() + "\t" + String.format("%.2f", score));
                context.write(outKey, outValue);
            } catch (NumberFormatException e) {
                context.getCounter("DQC", "BAD_NUMBER_FORMAT").increment(1);
            }
        }
    }

    // ==================== Partitioner ====================

    /** 按部门取模分桶：同部门必须进同一个 Reducer，否则 TopN 不完整 */
    public static class ScorePartitioner extends Partitioner<ScoreKey, Text> {
        @Override
        public int getPartition(ScoreKey key, Text value, int numPartitions) {
            return (key.getDeptId() & Integer.MAX_VALUE) % numPartitions;
        }
    }

    // ==================== 排序比较器 ====================

    /**
     * 全排序规则：部门升序，同部门分数降序。
     *
     * 直接比较序列化后的字节数组，跳过反序列化，比实现 Comparator&lt;ScoreKey&gt;
     * 少一次对象创建——Shuffle 阶段比较次数是千万级的，这个优化很关键。
     * 字节布局由 ScoreKey.write() 决定：前 4 字节 int 部门，后 8 字节 double 分数。
     */
    public static class ScoreSortComparator extends WritableComparator {

        protected ScoreSortComparator() {
            super(ScoreKey.class, true);
        }

        @Override
        public int compare(WritableComparable a, WritableComparable b) {
            ScoreKey k1 = (ScoreKey) a;
            ScoreKey k2 = (ScoreKey) b;
            int c = Integer.compare(k1.getDeptId(), k2.getDeptId());
            return c != 0 ? c : Double.compare(k2.getScore(), k1.getScore());
        }

        @Override
        public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
            int c = Integer.compare(readInt(b1, s1), readInt(b2, s2));
            if (c != 0) {
                return c;
            }
            // 分数降序：因此用 k2 减 k1 的方向
            return Double.compare(readDouble(b2, s2 + 4), readDouble(b1, s1 + 4));
        }
    }

    // ==================== 分组比较器 ====================

    /**
     * 分组规则：只看部门。
     *
     * 这样同部门的所有记录会被判定为"同一组"，进入同一次 reduce() 调用，
     * 我们才能在一次调用内按序遍历取前 N。
     */
    public static class DeptGroupComparator extends WritableComparator {

        protected DeptGroupComparator() {
            super(ScoreKey.class, true);
        }

        @Override
        public int compare(WritableComparable a, WritableComparable b) {
            return Integer.compare(((ScoreKey) a).getDeptId(), ((ScoreKey) b).getDeptId());
        }

        @Override
        public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
            return Integer.compare(readInt(b1, s1), readInt(b2, s2));
        }
    }

    // ==================== Reducer ====================

    public static class TopNReducer extends Reducer<ScoreKey, Text, Text, Text> {

        private final Text outKey = new Text();
        private final Text outValue = new Text();
        private int topN;

        @Override
        protected void setup(Context context) {
            topN = context.getConfiguration().getInt(CONF_TOP_N, 10);
        }

        @Override
        protected void reduce(ScoreKey key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            outKey.set(String.valueOf(key.getDeptId()));
            Set<Integer> seen = new HashSet<>();
            int rank = 0;
            for (Text v : values) {
                String[] p = v.toString().split("\t");
                if (p.length < 3) {
                    continue;
                }
                int empId = Integer.parseInt(p[0]);
                // 同一员工在 12 个月里会出现多次；因为已按分数降序排列，
                // 首次出现即其"最佳月度表现"，后续月份直接跳过，保证 TopN 不重复
                if (!seen.add(empId)) {
                    continue;
                }
                rank++;
                outValue.set(rank + "\t" + v);
                context.write(outKey, outValue);
                if (rank >= topN) {
                    break;
                }
            }
            context.getCounter("REDUCER", "DEPT_GROUPS").increment(1);
        }
    }

    // ==================== Driver ====================

    public static Job buildJob(Configuration conf, String inputPath, String outputPath,
                               int reducers, int topN) throws IOException {
        conf.setInt(CONF_TOP_N, topN);
        conf.setIfUnset("mapreduce.output.textoutputformat.separator", "\t");

        Job job = Job.getInstance(conf, "EMS-Department-TopN-SecondarySort");
        job.setJarByClass(DeptTopNJob.class);

        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        FileInputFormat.addInputPath(job, new Path(inputPath));
        FileOutputFormat.setOutputPath(job, new Path(outputPath));

        job.setMapperClass(TopNMapper.class);
        job.setPartitionerClass(ScorePartitioner.class);
        job.setSortComparatorClass(ScoreSortComparator.class);
        job.setGroupingComparatorClass(DeptGroupComparator.class);
        job.setReducerClass(TopNReducer.class);

        job.setMapOutputKeyClass(ScoreKey.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        job.setNumReduceTasks(Math.max(1, reducers));
        return job;
    }

    // ==================== 本地单测辅助（不依赖 Hadoop 环境） ====================

    /**
     * 纯逻辑验证：给定已按 (部门升序, 分数降序) 排好的记录，模拟 Reducer 的取数过程。
     * 用于在无集群环境下自检 TopN 与去重的正确性。
     */
    public static java.util.List<String> simulateTopN(java.util.List<String[]> sortedRecords, int topN) {
        java.util.List<String> result = new java.util.ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        int rank = 0;
        for (String[] r : sortedRecords) {
            int empId = Integer.parseInt(r[0]);
            if (!seen.add(empId)) {
                continue;
            }
            rank++;
            result.add(rank + "\t" + String.join("\t", r));
            if (rank >= topN) {
                break;
            }
        }
        return result;
    }
}
