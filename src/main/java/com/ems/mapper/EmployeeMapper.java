package com.ems.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ems.entity.Employee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 员工 Mapper
 *
 * 【学习要点——自定义SQL注解】
 * @Select：直接在接口方法上写SQL（适合简单查询）
 * 复杂SQL还是建议写XML映射文件，可读性更好
 */
@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {

    /**
     * 分页查询员工（关联查询部门名称）
     *
     * 这里用 @Select 注解演示简单SQL。
     * LEFT JOIN department 获取部门名称，填充到 Employee.deptName 字段。
     */
    @Select("SELECT e.*, d.name as dept_name " +
            "FROM employee e " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "${ew.customSqlSegment}")
    IPage<Employee> selectPageWithDept(Page<Employee> page, @Param("ew") com.baomidou.mybatisplus.core.conditions.Wrapper<Employee> wrapper);
}
