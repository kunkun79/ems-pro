package com.ems.bigdata.mr;

import org.apache.hadoop.io.WritableComparable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * 复合键：部门 + 统计月份
 *
 * 【作用】
 * 把"部门 × 月份"两个维度打包成一个 Key，MapReduce 才能按这个组合做分组聚合。
 * 排序规则：部门升序 → 月份升序，保证同一部门的数据在 Reduce 端连续到达，
 * 便于 Reduce 内做环比、累计这类需要顺序的计算。
 */
public class DeptMonthKey implements WritableComparable<DeptMonthKey> {

    private int deptId;
    private String month;

    public DeptMonthKey() {
        this.month = "";
    }

    public DeptMonthKey(int deptId, String month) {
        this.deptId = deptId;
        this.month = month == null ? "" : month;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeInt(deptId);
        out.writeUTF(month);
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        deptId = in.readInt();
        month = in.readUTF();
    }

    @Override
    public int compareTo(DeptMonthKey o) {
        int c = Integer.compare(this.deptId, o.deptId);
        if (c != 0) {
            return c;
        }
        return this.month.compareTo(o.month);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DeptMonthKey)) {
            return false;
        }
        DeptMonthKey other = (DeptMonthKey) o;
        return deptId == other.deptId && month.equals(other.month);
    }

    @Override
    public int hashCode() {
        return deptId * 31 + month.hashCode();
    }

    /** 复用对象，避免每条记录都 new 一个 Key（MR 高频调用下的常规优化） */
    public void set(int deptId, String month) {
        this.deptId = deptId;
        this.month = month == null ? "" : month;
    }

    /** 输出格式：直接以 "部门\t月份" 落盘，方便 Hive 外部表读取 */
    @Override
    public String toString() {
        return deptId + "\t" + month;
    }

    public int getDeptId() {
        return deptId;
    }

    public String getMonth() {
        return month;
    }
}
