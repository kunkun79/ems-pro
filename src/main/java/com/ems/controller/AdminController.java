package com.ems.controller;

import com.ems.common.Result;
import com.ems.entity.Admin;
import com.ems.service.AdminService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员控制器
 *
 * 【学习要点】
 * @RestController = @Controller + @ResponseBody（所有方法返回JSON）
 * @RequestMapping("/api/admin")：这个Controller下所有接口都以此开头
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    /**
     * 登录接口
     * POST /api/admin/login
     * 请求体：{ "username": "admin", "password": "123456" }
     */
    @PostMapping("/login")
    public Result<Admin> login(@RequestBody Map<String, String> params, HttpSession session) {
        String username = params.get("username");
        String password = params.get("password");

        Admin admin = adminService.login(username, password);
        if (admin != null) {
            // 登录成功，把用户信息存Session
            session.setAttribute("user", admin);
            // 不返回密码
            admin.setPassword(null);
            return Result.success("登录成功", admin);
        }
        return Result.error(401, "用户名或密码错误");
    }

    /**
     * 获取当前登录用户
     * GET /api/admin/current
     */
    @GetMapping("/current")
    public Result<Admin> currentUser(HttpSession session) {
        Admin admin = (Admin) session.getAttribute("user");
        if (admin != null) {
            admin.setPassword(null);
            return Result.success(admin);
        }
        return Result.error(401, "未登录");
    }

    /**
     * 修改密码
     * PUT /api/admin/password
     */
    @PutMapping("/password")
    public Result<String> changePassword(@RequestBody Map<String, String> params, HttpSession session) {
        Admin admin = (Admin) session.getAttribute("user");
        if (admin == null) {
            return Result.error(401, "请先登录");
        }
        String oldPwd = params.get("oldPassword");
        String newPwd = params.get("newPassword");
        boolean success = adminService.changePassword(admin.getId(), oldPwd, newPwd);
        return success ? Result.success("密码修改成功") : Result.error("旧密码错误");
    }

    /**
     * 退出登录
     * POST /api/admin/logout
     */
    @PostMapping("/logout")
    public Result<String> logout(HttpSession session) {
        session.invalidate();
        return Result.success("已退出");
    }
}
