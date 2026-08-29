package com.ems.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ems.entity.Employee;
import com.ems.mapper.EmployeeMapper;
import com.ems.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 员工服务实现
 */
@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    @Override
    public IPage<Employee> pageWithDept(int pageNum, int pageSize, String keyword) {
        Page<Employee> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();

        // 如果有关键词，按姓名模糊搜索
        // 【注意】使用 e.name 前缀避免与 department.name 冲突（LEFT JOIN 导致列歧义）
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.apply("e.name LIKE CONCAT('%', {0}, '%')", keyword);
        }
        wrapper.orderByAsc(Employee::getId);

        return employeeMapper.selectPageWithDept(page, wrapper);
    }

    @Override
    public Employee getById(Integer id) {
        return employeeMapper.selectById(id);
    }

    @Override
    public List<Employee> getByDeptId(Integer deptId) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Employee::getDeptId, deptId);
        return employeeMapper.selectList(wrapper);
    }

    @Override
    public List<Employee> searchByName(String name) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Employee::getName, name);
        return employeeMapper.selectList(wrapper);
    }

    @Override
    public boolean add(Employee employee) {
        return employeeMapper.insert(employee) > 0;
    }

    @Override
    public boolean update(Employee employee) {
        return employeeMapper.updateById(employee) > 0;
    }

    @Override
    public boolean delete(Integer id) {
        return employeeMapper.deleteById(id) > 0;
    }

    // ========== 统计分析（为大数据大屏提供数据） ==========

    @Override
    public List<Map<String, Object>> countByGender() {
        // 用Java Stream做聚合统计——这是"大数据思维"在小数据上的体现
        List<Employee> all = employeeMapper.selectList(null);
        Map<String, Long> genderCount = new HashMap<>();
        for (Employee emp : all) {
            String gender = emp.getGender() != null ? emp.getGender() : "未知";
            genderCount.merge(gender, 1L, Long::sum);
        }
        // 转换为前端需要的格式
        List<Map<String, Object>> result = new ArrayList<>();
        genderCount.forEach((k, v) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", k);
            map.put("value", v);
            result.add(map);
        });
        return result;
    }

    @Override
    public List<Map<String, Object>> countByDept() {
        // 使用关联查询获取部门人数
        List<Employee> all = employeeMapper.selectList(null);
        Map<Integer, Long> deptCount = new HashMap<>();
        for (Employee emp : all) {
            deptCount.merge(emp.getDeptId() != null ? emp.getDeptId() : 0, 1L, Long::sum);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        deptCount.forEach((k, v) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("deptId", k);
            map.put("count", v);
            result.add(map);
        });
        return result;
    }

    @Override
    public List<Map<String, Object>> countBySalaryRange() {
        List<Employee> all = employeeMapper.selectList(null);
        // 薪资区间：<10k, 10k-15k, 15k-20k, 20k-30k, >30k
        int[] ranges = {0, 0, 0, 0, 0};
        String[] labels = {"<1万", "1-1.5万", "1.5-2万", "2-3万", ">3万"};
        for (Employee emp : all) {
            if (emp.getSalary() == null) continue;
            double s = emp.getSalary().doubleValue();
            if (s < 10000) ranges[0]++;
            else if (s < 15000) ranges[1]++;
            else if (s < 20000) ranges[2]++;
            else if (s < 30000) ranges[3]++;
            else ranges[4]++;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < ranges.length; i++) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", labels[i]);
            map.put("value", ranges[i]);
            result.add(map);
        }
        return result;
    }

    // ========== 员工门户相关 ==========

    @Override
    public boolean register(Employee employee) {
        // 检查用户名是否已存在
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Employee::getUsername, employee.getUsername());
        if (employeeMapper.selectCount(wrapper) > 0) {
            return false;   // 用户名已存在
        }
        employee.setStatus("在职");
        return employeeMapper.insert(employee) > 0;
    }

    @Override
    public Employee login(String username, String password) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Employee::getUsername, username)
               .eq(Employee::getPassword, password);
        return employeeMapper.selectOne(wrapper);
    }

    @Override
    public Employee getByUsername(String username) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Employee::getUsername, username);
        return employeeMapper.selectOne(wrapper);
    }

    @Override
    public boolean updateProfile(Employee employee) {
        // 只更新个人信息字段，不更新部门/薪资/状态等管理字段
        Employee existing = employeeMapper.selectById(employee.getId());
        if (existing == null) return false;

        existing.setName(employee.getName());
        existing.setGender(employee.getGender());
        existing.setAge(employee.getAge());
        existing.setPhone(employee.getPhone());
        existing.setEmail(employee.getEmail());
        existing.setAddress(employee.getAddress());
        return employeeMapper.updateById(existing) > 0;
    }

    @Override
    public boolean changePassword(Integer id, String oldPassword, String newPassword) {
        Employee employee = employeeMapper.selectById(id);
        if (employee == null || !employee.getPassword().equals(oldPassword)) {
            return false;
        }
        employee.setPassword(newPassword);
        return employeeMapper.updateById(employee) > 0;
    }
}
