package com.ems.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ems.entity.Department;
import com.ems.mapper.DepartmentMapper;
import com.ems.service.DepartmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 部门服务实现
 */
@Service
public class DepartmentServiceImpl implements DepartmentService {

    @Autowired
    private DepartmentMapper departmentMapper;

    @Override
    public List<Department> listAll() {
        return departmentMapper.selectList(null);  // null = 查询所有
    }

    @Override
    public IPage<Department> page(int pageNum, int pageSize) {
        Page<Department> page = new Page<>(pageNum, pageSize);
        return departmentMapper.selectPage(page, null);
    }

    @Override
    public boolean add(Department department) {
        return departmentMapper.insert(department) > 0;
    }

    @Override
    public boolean update(Department department) {
        return departmentMapper.updateById(department) > 0;
    }

    @Override
    public boolean delete(Integer id) {
        return departmentMapper.deleteById(id) > 0;
    }
}
