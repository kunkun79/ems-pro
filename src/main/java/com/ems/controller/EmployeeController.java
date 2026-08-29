package com.ems.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ems.common.Result;
import com.ems.entity.Employee;
import com.ems.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 员工控制器
 */
@RestController
@RequestMapping("/api/employee")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;

    /** 分页查询（含部门名称） */
    @GetMapping("/page")
    public Result<IPage<Employee>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(employeeService.pageWithDept(page, size, keyword));
    }

    /** 按ID查询 */
    @GetMapping("/{id}")
    public Result<Employee> getById(@PathVariable Integer id) {
        Employee emp = employeeService.getById(id);
        return emp != null ? Result.success(emp) : Result.error("员工不存在");
    }

    /** 按部门查询 */
    @GetMapping("/byDept/{deptId}")
    public Result<List<Employee>> getByDeptId(@PathVariable Integer deptId) {
        return Result.success(employeeService.getByDeptId(deptId));
    }

    /** 按姓名搜索 */
    @GetMapping("/search")
    public Result<List<Employee>> search(@RequestParam String name) {
        return Result.success(employeeService.searchByName(name));
    }

    /** 新增员工 */
    @PostMapping
    public Result<String> add(@RequestBody Employee employee) {
        return employeeService.add(employee)
                ? Result.success("新增成功")
                : Result.error("新增失败");
    }

    /** 修改员工 */
    @PutMapping
    public Result<String> update(@RequestBody Employee employee) {
        return employeeService.update(employee)
                ? Result.success("修改成功")
                : Result.error("修改失败");
    }

    /** 删除员工 */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Integer id) {
        return employeeService.delete(id)
                ? Result.success("删除成功")
                : Result.error("删除失败");
    }
}
