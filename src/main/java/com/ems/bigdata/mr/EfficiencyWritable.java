package com.ems.bigdata.mr;

import org.apache.hadoop.io.Writable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * 效能指标聚合载体 —— 自定义 Writable
 *
 * 【为什么不用 Hadoop 内置类型？】
 * 一次聚合要同时传递 8 个指标（出勤天数、加班工时、任务量……）。
 * 若用 Text 拼字符串，Reduce 端要反复 split + parseDouble，序列化开销和 GC 压力都很大。
 * 自定义 Writable 直接按二进制字段读写，是 MapReduce 的标准做法，也是
 * 答辩时被追问"你如何减少 Shuffle 数据量"时的第一个得分点。
 *
 * 【序列化协议】
 * write(DataOutput) / readFields(DataInput) 必须严格对称：
 * 字段顺序、类型、个数一一对应，否则 Reduce 端反序列化会错位。
 */
public class EfficiencyWritable implements Writable {

    /** 考勤总天数（有记录的日期数） */
    private long days;
    /** 正常出勤天数（leave_type = 正常） */
    private long normalDays;
    /** 迟到次数（late_minutes > 0 记一次） */
    private long lateTimes;
    /** 迟到分钟累计 */
    private long lateMinutes;
    /** 加班小时累计 */
    private double overtimeHours;
    /** 实际工时累计 */
    private double workHours;
    /** 任务总数 */
    private long taskCount;
    /** 已完成任务数 */
    private long taskDone;
    /** 质量分累计（仅统计已完成任务） */
    private double qualitySum;
    /** 脏数据条数（清洗阶段判定为异常的记录） */
    private long dirty;

    public EfficiencyWritable() {
    }

    // ========== Writeable 序列化 ==========

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeLong(days);
        out.writeLong(normalDays);
        out.writeLong(lateTimes);
        out.writeLong(lateMinutes);
        out.writeDouble(overtimeHours);
        out.writeDouble(workHours);
        out.writeLong(taskCount);
        out.writeLong(taskDone);
        out.writeDouble(qualitySum);
        out.writeLong(dirty);
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        days = in.readLong();
        normalDays = in.readLong();
        lateTimes = in.readLong();
        lateMinutes = in.readLong();
        overtimeHours = in.readDouble();
        workHours = in.readDouble();
        taskCount = in.readLong();
        taskDone = in.readLong();
        qualitySum = in.readDouble();
        dirty = in.readLong();
    }

    // ========== 聚合操作（Combiner / Reducer 复用） ==========

    /** 将另一条记录累加到本对象（原地修改，避免频繁创建对象） */
    public void merge(EfficiencyWritable other) {
        if (other == null) {
            return;
        }
        this.days += other.days;
        this.normalDays += other.normalDays;
        this.lateTimes += other.lateTimes;
        this.lateMinutes += other.lateMinutes;
        this.overtimeHours += other.overtimeHours;
        this.workHours += other.workHours;
        this.taskCount += other.taskCount;
        this.taskDone += other.taskDone;
        this.qualitySum += other.qualitySum;
        this.dirty += other.dirty;
    }

    /** 归零复用对象：Combiner / Reducer 循环外创建对象，循环内 reset，避免 GC 压力 */
    public void reset() {
        days = 0;
        normalDays = 0;
        lateTimes = 0;
        lateMinutes = 0;
        overtimeHours = 0;
        workHours = 0;
        taskCount = 0;
        taskDone = 0;
        qualitySum = 0;
        dirty = 0;
    }

    // ========== 派生指标（口径与 Hive DWS 层保持一致） ==========

    /** 出勤率 = 正常出勤天数 / 考勤天数 */
    public double getAttendanceRate() {
        return days == 0 ? 0 : (double) normalDays / days;
    }

    /** 准时率 = 1 - 迟到次数 / 考勤天数 */
    public double getPunctualRate() {
        return days == 0 ? 0 : 1.0 - (double) lateTimes / days;
    }

    /** 任务完成率 = 已完成任务数 / 任务总数 */
    public double getCompleteRate() {
        return taskCount == 0 ? 0 : (double) taskDone / taskCount;
    }

    /** 平均质量分（已完成任务的均值，任务难度已折算进 quality_score） */
    public double getAvgQuality() {
        return taskDone == 0 ? 0 : qualitySum / taskDone;
    }

    /** 人均日均加班小时 */
    public double getAvgOvertime() {
        return days == 0 ? 0 : overtimeHours / days;
    }

    /** 人均日均工时 */
    public double getAvgWorkHours() {
        return days == 0 ? 0 : workHours / days;
    }

    /**
     * 效能指数（0~100）
     *
     * 权重与数仓 DWS 层 dws_emp_efficiency_month 完全一致，保证 MR 与 Hive 两条链路
     * 算出的结果可交叉验证——这是论文中"计算一致性验证"实验的基础。
     *   0.30 × 任务完成率 + 0.25 × KPI(质量分折算) + 0.20 × 出勤率
     * + 0.15 × 准时率     + 0.10 × 加班贡献度
     */
    public double getEfficiencyIndex() {
        double qualityScore = clamp(getAvgQuality() / 10.0, 0, 1);
        double overtimeScore = clamp(getAvgOvertime() / 4.0, 0, 1);
        return 100 * (0.30 * getCompleteRate()
                + 0.25 * qualityScore
                + 0.20 * getAttendanceRate()
                + 0.15 * getPunctualRate()
                + 0.10 * overtimeScore);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    // ========== 工厂方法 ==========

    /** 由一条考勤明细构造 */
    public static EfficiencyWritable ofAttendance(long lateMinutes, double overtimeHours,
                                                  double workHours, boolean normal, long dirty) {
        EfficiencyWritable w = new EfficiencyWritable();
        w.days = 1;
        w.normalDays = normal ? 1 : 0;
        w.lateTimes = lateMinutes > 0 ? 1 : 0;
        w.lateMinutes = lateMinutes;
        w.overtimeHours = overtimeHours;
        w.workHours = workHours;
        w.dirty = dirty;
        return w;
    }

    /** 由一条任务明细构造 */
    public static EfficiencyWritable ofTask(boolean done, double qualityScore) {
        EfficiencyWritable w = new EfficiencyWritable();
        w.taskCount = 1;
        if (done) {
            w.taskDone = 1;
            w.qualitySum = qualityScore;
        }
        return w;
    }

    // ========== Getter / Setter ==========

    public long getDays() {
        return days;
    }

    public long getNormalDays() {
        return normalDays;
    }

    public long getLateTimes() {
        return lateTimes;
    }

    public long getLateMinutes() {
        return lateMinutes;
    }

    public double getOvertimeHours() {
        return overtimeHours;
    }

    public double getWorkHours() {
        return workHours;
    }

    public long getTaskCount() {
        return taskCount;
    }

    public long getTaskDone() {
        return taskDone;
    }

    public double getQualitySum() {
        return qualitySum;
    }

    public long getDirty() {
        return dirty;
    }

    @Override
    public String toString() {
        return String.format(
                "days=%d normal=%d late=%d lateMin=%d ot=%.2f work=%.2f task=%d done=%d dirty=%d",
                days, normalDays, lateTimes, lateMinutes, overtimeHours, workHours,
                taskCount, taskDone, dirty);
    }
}
