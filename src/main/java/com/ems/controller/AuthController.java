package com.ems.controller;

import com.ems.common.Result;
import com.ems.entity.Admin;
import com.ems.entity.Employee;
import com.ems.service.AdminService;
import com.ems.service.EmployeeService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一登录控制器
 *
 * 同一套表单，根据用户名自动识别身份：
 * - admin → 管理员（仅此一个账号）
 * - 其他 → 员工
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private static final String ADMIN_SESSION_KEY = "user";
    private static final String EMP_SESSION_KEY = "empUser";

    @Autowired
    private AdminService adminService;

    @Autowired
    private EmployeeService employeeService;

    /**
     * 统一登录
     * POST /api/login
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> params, HttpSession session) {
        String username = params.get("username");
        String password = params.get("password");

        // 先尝试管理员登录（只有 username == "admin" 才走管理线路）
        if ("admin".equals(username)) {
            Admin admin = adminService.login(username, password);
            if (admin != null) {
                session.setAttribute(ADMIN_SESSION_KEY, admin);
                admin.setPassword(null);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("role", "ADMIN");
                result.put("redirect", "/dashboard");
                result.put("user", admin);
                return Result.success("登录成功", result);
            } else {
                return Result.error(401, "管理员密码错误");
            }
        }

        // 员工登录
        Employee emp = employeeService.login(username, password);
        if (emp != null) {
            session.setAttribute(EMP_SESSION_KEY, emp);
            emp.setPassword(null);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("role", "EMPLOYEE");
            result.put("redirect", "/emp-profile");
            result.put("user", emp);
            return Result.success("登录成功", result);
        }
        return Result.error(401, "用户名或密码错误");
    }

    /**
     * 统一退出（同时清除两个 session key）
     * POST /api/logout
     */
    @PostMapping("/logout")
    public Result<String> logout(HttpSession session) {
        session.removeAttribute(ADMIN_SESSION_KEY);
        session.removeAttribute(EMP_SESSION_KEY);
        return Result.success("已退出");
    }
}
