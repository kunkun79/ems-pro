package com.ems.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ems.entity.Attendance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 考勤 Mapper
 *
 * 除 MyBatis Plus 内置 CRUD 外，提供按员工分批抽取的查询方法，
 * 供 ODS 层大数据量抽取时使用（避免一次性载入全部记录导致 OOM）。
 */
@Mapper
public interface AttendanceMapper extends BaseMapper<Attendance> {

    /**
     * 分页抽取考勤数据（供 ETL 分批处理）
     */
    @Select("SELECT id, emp_id, work_date, sign_in_time, sign_out_time, " +
            "late_minutes, overtime_hours, leave_type, work_hours " +
            "FROM attendance ORDER BY id LIMIT #{offset}, #{limit}")
    List<Attendance> selectByPage(@Param("offset") long offset, @Param("limit") int limit);
}
