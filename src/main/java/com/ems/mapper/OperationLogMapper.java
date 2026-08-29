package com.ems.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ems.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 操作日志 Mapper（大数据分析的数据源）
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {

    /**
     * 按操作类型统计（如：新增30次，修改25次...）
     */
    @Select("SELECT action as name, COUNT(*) as value FROM operation_log GROUP BY action")
    List<Map<String, Object>> countByAction();

    /**
     * 按日期统计操作量（折线图数据）
     */
    @Select("SELECT DATE(create_time) as date, COUNT(*) as count " +
            "FROM operation_log " +
            "GROUP BY DATE(create_time) " +
            "ORDER BY date")
    List<Map<String, Object>> countByDate();

    /**
     * 按小时统计操作量（热力图数据）
     */
    @Select("SELECT HOUR(create_time) as hour, COUNT(*) as count " +
            "FROM operation_log GROUP BY HOUR(create_time) ORDER BY hour")
    List<Map<String, Object>> countByHour();
}
