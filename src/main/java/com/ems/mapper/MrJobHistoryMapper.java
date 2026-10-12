package com.ems.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ems.entity.MrJobHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * MapReduce 作业历史 Mapper
 *
 * 大屏读取最近作业记录；P4 性能对比实验也基于这张表做结果汇总。
 */
@Mapper
public interface MrJobHistoryMapper extends BaseMapper<MrJobHistory> {

    /** 最近 N 条作业记录，按执行时间倒序 */
    @Select("SELECT * FROM mr_job_history ORDER BY id DESC LIMIT #{limit}")
    List<MrJobHistory> selectRecent(@Param("limit") int limit);

    /** 每个作业各取最近一条（大屏「作业面板」用，避免同一作业刷屏） */
    @Select("SELECT * FROM mr_job_history h " +
            "WHERE id = (SELECT MAX(id) FROM mr_job_history x WHERE x.job_name = h.job_name) " +
            "ORDER BY id DESC")
    List<MrJobHistory> selectLatestPerJob();

    /** 汇总：作业总数、成功数、累计 Shuffle 字节、累计处理记录数 */
    @Select("SELECT COUNT(*) AS total, " +
            "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successCount, " +
            "SUM(map_output_bytes) AS shuffleBytes, " +
            "SUM(map_input_records) AS inputRecords, " +
            "SUM(duration_ms) AS totalDuration " +
            "FROM mr_job_history")
    Map<String, Object> summary();

    /** 按 Reduce 数分组的平均耗时（P4 加速比实验用，忽略 Reduce 数为 0 的历史脏记录） */
    @Select("SELECT launched_reduces AS reduces, COUNT(*) AS runs, " +
            "AVG(duration_ms) AS avgDuration, AVG(map_output_bytes) AS avgShuffle " +
            "FROM mr_job_history WHERE job_name = #{jobName} AND success = 1 " +
            "AND launched_reduces > 0 " +
            "GROUP BY launched_reduces ORDER BY launched_reduces")
    List<Map<String, Object>> groupByReduces(@Param("jobName") String jobName);
}
