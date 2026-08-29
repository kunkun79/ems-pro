package com.ems.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ems.entity.Employee;

import java.util.List;

/**
 * 员工服务接口
 */
public interface EmployeeService {

    /** 分页查询（含部门名称） */
    IPage<Employee> pageWithDept(int pageNum, int pageSize, String keyword);

    /** 按ID查询 */
    Employee getById(Integer id);

    /** 按部门查询 */
    List<Employee> getByDeptId(Integer deptId);

    /** 按姓名模糊搜索 */
    List<Employee> searchByName(String name);

    /** 新增员工 */
    boolean add(Employee employee);

    /** 更新员工 */
    boolean update(Employee employee);

    /** 删除员工 */
    boolean delete(Integer id);

    /** 统计：性别分布 */
    List<java.util.Map<String, Object>> countByGender();

    /** 统计：部门人数 */
    List<java.util.Map<String, Object>> countByDept();

    /** 统计：薪资区间分布 */
    List<java.util.Map<String, Object>> countBySalaryRange();

    // ========== 员工门户相关 ==========

    /** 员工注册 */
    boolean register(Employee employee);

    /** 员工登录（用户名+密码验证） */
    Employee login(String username, String password);

    /** 按用户名查询 */
    Employee getByUsername(String username);

    /** 员工修改自己的个人资料（仅限个人信息字段） */
    boolean updateProfile(Employee employee);

    /** 员工修改密码 */
    boolean changePassword(Integer id, String oldPassword, String newPassword);
}
