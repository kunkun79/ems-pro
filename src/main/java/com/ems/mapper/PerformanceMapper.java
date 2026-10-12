package com.ems.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ems.entity.Performance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 绩效考核 Mapper
 */
@Mapper
public interface PerformanceMapper extends BaseMapper<Performance> {

    @Select("SELECT id, emp_id, period, kpi_score, grade " +
            "FROM performance ORDER BY id LIMIT #{offset}, #{limit}")
    List<Performance> selectByPage(@Param("offset") long offset, @Param("limit") int limit);
}
