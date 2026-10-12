package com.ems.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ems.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 任务工单 Mapper
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    @Select("SELECT id, emp_id, task_code, task_type, difficulty, " +
            "assign_date, due_date, finish_date, status, quality_score " +
            "FROM task ORDER BY id LIMIT #{offset}, #{limit}")
    List<Task> selectByPage(@Param("offset") long offset, @Param("limit") int limit);
}
