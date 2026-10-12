package com.ems.bigdata.mr;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.Counters;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import java.io.IOException;

/**
 * Job 1：部门 × 月份 员工效能聚合
 *
 * 【业务目标】
 * 输入 DWD 层考勤明细，输出每个部门每月的出勤率、准时率、平均加班、人均工时，
 * 供 DWS 层部门画像与前端看板使用。
 *
 * 【本 Job 展示的 MapReduce 核心能力】
 *   1. 自定义 Writable 作为传输值（EfficiencyWritable），一次 Shuffle 携带多个指标
 *   2. Combiner 局部预聚合：Map 端先按 (部门,月份) 合并，
 *      Shuffle 数据量从"每人每天一条"压缩到"每部门每月一条"
 *   3. 自定义 Partitioner：按部门哈希分桶，保证同部门数据落到同一 Reducer，
 *      同时避免默认 HashPartitioner 因 month 参与哈希导致的数据倾斜
 *   4. Counter 数据质量监控：脏数据不丢弃，而是计数后单独统计，
 *      作业结束后可从 Counters 中读出，作为 DQC（数据质量校验）报告来源
 *
 * 【输入格式】（DWD 考勤事实，\t 分隔）
 *   emp_id  dept_id  work_date  sign_in  sign_out  late_minutes
 *   overtime_hours  leave_type  work_hours
 *
 * 【输出格式】
 *   key:   dept_id \t month
 *   value: 天数 \t 正常天数 \t 迟到次数 \t 迟到分钟 \t 加班小时 \t 工时
 *          \t 出勤率 \t 准时率 \t 人均加班 \t 人均工时 \t 脏数据条数
 */
public class DeptEfficiencyJob {

    // ==================== Mapper ====================

    public static class EfficiencyMapper
            extends Mapper<LongWritable, Text, DeptMonthKey, EfficiencyWritable> {

        private final DeptMonthKey outKey = new DeptMonthKey();
        private EfficiencyWritable outValue;

        /** 合法工时区间：用于脏数据判定（生成器注入了 25~72 小时的异常值） */
        private static final double MIN_WORK_HOURS = 0;
        private static final double MAX_WORK_HOURS = 24;

        @Override
        protected void map(LongWritable key, Text value, Context context)
                throws IOException, InterruptedException {
            String line = value.toString();
            if (line.isEmpty() || line.startsWith("#")) {
                return;
            }
            String[] f = line.split("\t");
            if (f.length < 9) {
                context.getCounter("DQC", "BAD_FIELD_COUNT").increment(1);
                return;
            }

            int deptId;
            long lateMinutes;
            double overtime;
            double workHours;
            try {
                deptId = Integer.parseInt(f[1].trim());
                lateMinutes = (long) Double.parseDouble(f[5].trim());
                overtime = Double.parseDouble(f[6].trim());
                workHours = Double.parseDouble(f[8].trim());
            } catch (NumberFormatException e) {
                context.getCounter("DQC", "BAD_NUMBER_FORMAT").increment(1);
                return;
            }

            // 月份取自 work_date（yyyy-MM-dd）前 7 位
            String month = f[2].trim().substring(0, Math.min(7, f[2].trim().length()));
            String leaveType = f[7].trim();

            // ---- 数据质量校验：异常值不丢弃，标记后计入 Counter ----
            long dirty = 0;
            if (workHours < MIN_WORK_HOURS || workHours > MAX_WORK_HOURS) {
                context.getCounter("DQC", "BAD_WORK_HOURS").increment(1);
                dirty = 1;
                // 修正策略：截断到边界值，保证聚合结果不受极端值污染
                workHours = Math.min(MAX_WORK_HOURS, Math.max(MIN_WORK_HOURS, workHours));
            }
            if (overtime < 0 || overtime > 16) {
                context.getCounter("DQC", "BAD_OVERTIME").increment(1);
                overtime = Math.min(16, Math.max(0, overtime));
            }
            // 是否应出勤：请假 / 旷工 / 调休当天本来就没有打卡记录，属正常业务而非脏数据
            boolean normal = "正常".equals(leaveType);
            boolean signInMissing = f[3].trim().isEmpty() || "NULL".equalsIgnoreCase(f[3].trim());
            if (signInMissing) {
                if (normal) {
                    // 应出勤却无签到：真正的数据缺失，计入 DQC 异常
                    context.getCounter("DQC", "MISSING_SIGN_IN").increment(1);
                    dirty = 1;
                } else {
                    // 请假/旷工导致的无签到：业务上合理，单独统计以便与异常区分
                    context.getCounter("DQC", "LEAVE_NO_SIGNIN").increment(1);
                }
            }

            outKey.set(deptId, month);
            outValue = EfficiencyWritable.ofAttendance(lateMinutes, overtime, workHours, normal, dirty);
            context.write(outKey, outValue);
            context.getCounter("DQC", "TOTAL_VALID_RECORDS").increment(1);
        }
    }

    // ==================== Combiner ====================

    /**
     * Combiner：在 Map 端做一次局部聚合。
     *
     * 【为什么这里可以直接复用 Reducer？】
     * 本聚合满足交换律与结合律（加法），所以 Map 端先合并、Reduce 端再合并，
     * 结果与不启用 Combiner 完全一致。这就是为什么 Combiner 能用 Reducer 类。
     * 注意：求平均数这类不满足结合律的运算不能直接复用，必须输出 (sum, count) 让
     * Reduce 端最后再做除法——本 Job 正是这么做的：中间传的是累计值，
     * 平均值在 Reduce 端由 getAttendanceRate() 等方法计算。
     */
    public static class EfficiencyCombiner
            extends Reducer<DeptMonthKey, EfficiencyWritable, DeptMonthKey, EfficiencyWritable> {

        private final EfficiencyWritable sum = new EfficiencyWritable();

        @Override
        protected void reduce(DeptMonthKey key, Iterable<EfficiencyWritable> values, Context context)
                throws IOException, InterruptedException {
            sum.reset();
            int n = 0;
            for (EfficiencyWritable v : values) {
                sum.merge(v);
                n++;
            }
            context.getCounter("COMBINER", "INPUT_GROUPS").increment(1);
            context.getCounter("COMBINER", "RECORDS_MERGED").increment(n);
            context.write(key, sum);
        }
    }

    // ==================== Partitioner ====================

    /**
     * 按部门哈希分区。
     *
     * 【为什么不用默认 HashPartitioner？】
     * 默认实现用 key.hashCode() % numReduceTasks，而 DeptMonthKey 的 hashCode 里
     * 混入了 month，同一个部门会散到不同 Reducer，既无法保证组内顺序，
     * 也可能因某些月份数据量暴增造成 Reduce 端倾斜。这里显式按 deptId 取模。
     */
    public static class DeptPartitioner extends Partitioner<DeptMonthKey, EfficiencyWritable> {
        @Override
        public int getPartition(DeptMonthKey key, EfficiencyWritable value, int numPartitions) {
            return (key.getDeptId() & Integer.MAX_VALUE) % numPartitions;
        }
    }

    // ==================== Reducer ====================

    public static class EfficiencyReducer
            extends Reducer<DeptMonthKey, EfficiencyWritable, DeptMonthKey, Text> {

        private final EfficiencyWritable total = new EfficiencyWritable();
        private final Text outValue = new Text();

        @Override
        protected void reduce(DeptMonthKey key, Iterable<EfficiencyWritable> values, Context context)
                throws IOException, InterruptedException {
            total.reset();
            for (EfficiencyWritable v : values) {
                total.merge(v);
            }
            if (total.getDays() == 0) {
                return;
            }
            outValue.set(String.format(
                    "%d\t%d\t%d\t%d\t%.2f\t%.2f\t%.4f\t%.4f\t%.3f\t%.3f\t%d",
                    total.getDays(), total.getNormalDays(), total.getLateTimes(),
                    total.getLateMinutes(), total.getOvertimeHours(), total.getWorkHours(),
                    total.getAttendanceRate(), total.getPunctualRate(),
                    total.getAvgOvertime(), total.getAvgWorkHours(), total.getDirty()));
            context.write(key, outValue);
            context.getCounter("REDUCER", "OUTPUT_GROUPS").increment(1);
        }
    }

    // ==================== Driver ====================

    /** 组装并提交作业，返回 Job 句柄供调用方读取 Counters */
    public static Job buildJob(Configuration conf, String inputPath, String outputPath, int reducers)
            throws IOException {
        conf.setIfUnset("mapreduce.output.textoutputformat.separator", "\t");
        Job job = Job.getInstance(conf, "EMS-Department-Efficiency");
        job.setJarByClass(DeptEfficiencyJob.class);

        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        FileInputFormat.addInputPath(job, new Path(inputPath));
        FileOutputFormat.setOutputPath(job, new Path(outputPath));

        job.setMapperClass(EfficiencyMapper.class);
        job.setCombinerClass(EfficiencyCombiner.class);
        job.setPartitionerClass(DeptPartitioner.class);
        job.setReducerClass(EfficiencyReducer.class);

        job.setMapOutputKeyClass(DeptMonthKey.class);
        job.setMapOutputValueClass(EfficiencyWritable.class);
        job.setOutputKeyClass(DeptMonthKey.class);
        job.setOutputValueClass(Text.class);

        job.setNumReduceTasks(Math.max(1, reducers));
        return job;
    }

    /** 从 Counters 中提取数据质量指标，便于组装 DQC 报告 */
    public static java.util.Map<String, Long> extractDqcCounters(Counters counters) {
        java.util.Map<String, Long> dqc = new java.util.LinkedHashMap<>();
        String[] names = {"TOTAL_VALID_RECORDS", "BAD_FIELD_COUNT", "BAD_NUMBER_FORMAT",
                "BAD_WORK_HOURS", "BAD_OVERTIME", "MISSING_SIGN_IN", "LEAVE_NO_SIGNIN"};
        for (String n : names) {
            Counter c = counters.findCounter("DQC", n);
            if (c != null) {
                dqc.put(n, c.getValue());
            }
        }
        return dqc;
    }
}
