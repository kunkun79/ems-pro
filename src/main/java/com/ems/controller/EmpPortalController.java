package com.ems.controller;

import com.ems.common.Result;
import com.ems.entity.Employee;
import com.ems.service.EmployeeService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 员工门户控制器
 *
 * 面向员工本人的自助服务：注册、登录、查看/修改个人资料、修改密码。
 * 使用 HttpSession 维护登录态（与管理员侧模式一致）。
 */
@RestController
@RequestMapping("/api/emp-portal")
public class EmpPortalController {

    private static final String SESSION_KEY = "empUser";

    @Autowired
    private EmployeeService employeeService;

    /**
     * 员工注册
     * POST /api/emp-portal/register
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody Employee employee) {
        if (employee.getUsername() == null || employee.getUsername().isBlank()) {
            return Result.error("用户名不能为空");
        }
        if (employee.getPassword() == null || employee.getPassword().isBlank()) {
            return Result.error("密码不能为空");
        }
        if (employee.getName() == null || employee.getName().isBlank()) {
            return Result.error("姓名不能为空");
        }
        boolean ok = employeeService.register(employee);
        return ok ? Result.success("注册成功") : Result.error("注册失败，用户名已存在");
    }

    /**
     * 员工登录
     * POST /api/emp-portal/login
     */
    @PostMapping("/login")
    public Result<Employee> login(@RequestBody Map<String, String> params, HttpSession session) {
        String username = params.get("username");
        String password = params.get("password");
        Employee emp = employeeService.login(username, password);
        if (emp != null) {
            session.setAttribute(SESSION_KEY, emp);
            emp.setPassword(null);
            return Result.success("登录成功", emp);
        }
        return Result.error(401, "用户名或密码错误");
    }

    /**
     * 获取当前登录员工信息
     * GET /api/emp-portal/current
     */
    @GetMapping("/current")
    public Result<Employee> currentUser(HttpSession session) {
        Employee emp = (Employee) session.getAttribute(SESSION_KEY);
        if (emp == null) {
            return Result.error(401, "未登录");
        }
        // 重新从数据库查，避免 Session 中数据过时
        Employee latest = employeeService.getById(emp.getId());
        if (latest == null) {
            return Result.error(401, "用户不存在");
        }
        latest.setPassword(null);
        return Result.success(latest);
    }

    /**
     * 修改个人资料
     * PUT /api/emp-portal/profile
     */
    @PutMapping("/profile")
    public Result<String> updateProfile(@RequestBody Employee employee, HttpSession session) {
        Employee emp = (Employee) session.getAttribute(SESSION_KEY);
        if (emp == null) {
            return Result.error(401, "请先登录");
        }
        employee.setId(emp.getId());          // 强制只能改自己的
        boolean ok = employeeService.updateProfile(employee);
        if (ok) {
            // 同步更新 Session 中的姓名
            emp.setName(employee.getName());
            session.setAttribute(SESSION_KEY, emp);
        }
        return ok ? Result.success("修改成功") : Result.error("修改失败");
    }

    /**
     * 修改密码
     * PUT /api/emp-portal/password
     */
    @PutMapping("/password")
    public Result<String> changePassword(@RequestBody Map<String, String> params, HttpSession session) {
        Employee emp = (Employee) session.getAttribute(SESSION_KEY);
        if (emp == null) {
            return Result.error(401, "请先登录");
        }
        String oldPwd = params.get("oldPassword");
        String newPwd = params.get("newPassword");
        if (newPwd == null || newPwd.isBlank()) {
            return Result.error("新密码不能为空");
        }
        boolean ok = employeeService.changePassword(emp.getId(), oldPwd, newPwd);
        return ok ? Result.success("密码修改成功") : Result.error("旧密码错误");
    }

    /**
     * 退出登录
     * POST /api/emp-portal/logout
     */
    @PostMapping("/logout")
    public Result<String> logout(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
        return Result.success("已退出");
    }
}
