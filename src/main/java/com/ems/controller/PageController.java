package com.ems.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面路由控制器
 *
 * 【学习要点】
 * 这里用 @Controller（不是 @RestController），
 * 因为返回值是模板名称（不是JSON），Thymeleaf会渲染为HTML页面。
 */
@Controller
public class PageController {

    @GetMapping("/")
    public String index() { return "index"; }

    @GetMapping("/login")
    public String login() { return "login"; }

    @GetMapping("/dashboard")
    public String dashboard() { return "dashboard"; }

    @GetMapping("/employee")
    public String employee() { return "employee"; }

    @GetMapping("/department")
    public String department() { return "department"; }

    @GetMapping("/bigdata")
    public String bigdata() { return "bigdata"; }

    /** 效能数据可视化（一年期考勤/任务/绩效数据的多维分析） */
    @GetMapping("/efficiency")
    public String efficiency() { return "efficiency"; }

    @GetMapping("/bigdata-screen")
    public String bigdataScreen() { return "bigdata-screen"; }

    @GetMapping("/admin")
    public String admin() { return "admin"; }

    // ========== 员工门户页面 ==========

    @GetMapping("/emp-login")
    public String empLogin() { return "redirect:/login"; }

    @GetMapping("/emp-register")
    public String empRegister() { return "emp-register"; }

    @GetMapping("/emp-profile")
    public String empProfile() { return "emp-profile"; }
}
