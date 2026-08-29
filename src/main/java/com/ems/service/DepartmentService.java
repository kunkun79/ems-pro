package com.ems.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ems.entity.Department;

import java.util.List;

/**
 * 部门服务接口
 */
public interface DepartmentService {

    /** 查询所有部门 */
    List<Department> listAll();

    /** 分页查询 */
    IPage<Department> page(int pageNum, int pageSize);

    /** 新增部门 */
    boolean add(Department department);

    /** 修改部门 */
    boolean update(Department department);

    /** 删除部门 */
    boolean delete(Integer id);
}
