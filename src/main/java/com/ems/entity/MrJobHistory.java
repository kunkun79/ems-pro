package com.ems.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MapReduce 作业执行历史
 *
 * 【为什么要有这张表】
 * 作业跑完后 Hadoop 的 Counters 只存在于当次 JVM 内存中，页面一刷新就没了。
 * 大屏的「计算引擎可观测」栏需要展示历史作业的 Shuffle 规模、耗时、DQC 结果，
 * 因此每次作业结束把关键计数器落库，大屏直接读表。
 *
 * 顺带这张表本身就是论文里的「作业执行记录表」——
 * P4 性能对比实验（不同 Reduce 数、两种 Join 方式）的结果也存这里，
 * 可以直接导出成对比表。
 */
@Data
@TableName("mr_job_history")
public class MrJobHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作业名称，如「部门月度效能聚合」 */
    private String jobName;

    /** Hadoop JobID，local 模式下形如 job_local123456789_0001 */
    private String jobId;

    /** 仅 Join 作业有值：map-side / reduce-side */
    private String joinMode;

    /** 运行框架：local / yarn */
    private String framework;

    /** 1 成功 0 失败 */
    private Integer success;

    /** 耗时毫秒 */
    private Long durationMs;

    private Long mapInputRecords;
    private Long mapOutputRecords;
    /** Map 输出字节数，代表 Shuffle 数据规模 */
    private Long mapOutputBytes;
    private Long combineInputRecords;
    private Long combineOutputRecords;
    private Long spilledRecords;
    private Long reduceInputGroups;
    private Long reduceInputRecords;
    private Long reduceOutputRecords;
    private Integer launchedMaps;
    private Integer launchedReduces;

    /** DQC 校验总数与异常数 */
    private Long dqcTotal;
    private Long dqcAbnormal;

    private String errorMsg;
    private String outputPath;
    private LocalDateTime createTime;
}
