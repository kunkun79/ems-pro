package com.ems.bigdata.mr;

import org.apache.hadoop.io.WritableComparable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * 二次排序键：部门 + 效能分
 *
 * 【二次排序（Secondary Sort）解决什么问题？】
 * 需求：每个部门取效能分 TopN 的员工。
 * 朴素做法是把一个部门所有员工都拉进 Reducer，在内存里排序取前 N——
 * 部门人多时会 OOM，且 Reducer 要缓存全量数据。
 *
 * 二次排序的做法：让 Shuffle 阶段就把数据排好（部门升序 + 分数降序），
 * Reducer 只取每组前 N 条即可退出，内存占用恒定。
 *
 * 它由三个可插拔组件共同完成，缺一不可：
 *   1. Partitioner        —— 按部门分桶，保证同部门进同一个 Reducer
 *   2. SortComparator     —— 全排序规则：部门升序 + 分数降序
 *   3. GroupingComparator —— 分组规则：只看部门，让同部门记录进同一次 reduce() 调用
 */
public class ScoreKey implements WritableComparable<ScoreKey> {

    private int deptId;
    private double score;

    public ScoreKey() {
    }

    public ScoreKey(int deptId, double score) {
        this.deptId = deptId;
        this.score = score;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeInt(deptId);
        out.writeDouble(score);
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        deptId = in.readInt();
        score = in.readDouble();
    }

    /** 复用对象，避免每条记录都 new 一个 Key */
    public void set(int deptId, double score) {
        this.deptId = deptId;
        this.score = score;
    }

    /** 默认比较：部门升序，同部门按分数降序（高分在前） */
    @Override
    public int compareTo(ScoreKey o) {
        int c = Integer.compare(this.deptId, o.deptId);
        if (c != 0) {
            return c;
        }
        return Double.compare(o.score, this.score);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ScoreKey)) {
            return false;
        }
        ScoreKey other = (ScoreKey) o;
        return deptId == other.deptId && Double.compare(score, other.score) == 0;
    }

    @Override
    public int hashCode() {
        return deptId * 31 + Double.hashCode(score);
    }

    @Override
    public String toString() {
        return deptId + "\t" + String.format("%.2f", score);
    }

    public int getDeptId() {
        return deptId;
    }

    public double getScore() {
        return score;
    }
}
