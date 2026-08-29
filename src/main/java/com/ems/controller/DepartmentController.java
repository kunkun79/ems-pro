package com.ems.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ems.common.Result;
import com.ems.entity.Department;
import com.ems.service.DepartmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门控制器
 *
 * 【学习要点——RESTful API 设计规范】
 * GET    /api/department        → 查询所有
 * GET    /api/department/{id}   → 查询单个
 * POST   /api/department        → 新增
 * PUT    /api/department        → 修改
 * DELETE /api/department/{id}   → 删除
 *
 * 这个规范让API语义清晰，配合HTTP方法即可知道操作类型。
 */
@RestController
@RequestMapping("/api/department")
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    /** 查询所有部门 */
    @GetMapping
    public Result<List<Department>> listAll() {
        return Result.success(departmentService.listAll());
    }

    /** 分页查询 */
    @GetMapping("/page")
    public Result<IPage<Department>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(departmentService.page(page, size));
    }

    /** 新增部门 */
    @PostMapping
    public Result<String> add(@RequestBody Department department) {
        return departmentService.add(department)
                ? Result.success("新增成功")
                : Result.error("新增失败");
    }

    /** 修改部门 */
    @PutMapping
    public Result<String> update(@RequestBody Department department) {
        return departmentService.update(department)
                ? Result.success("修改成功")
                : Result.error("修改失败");
    }

    /** 删除部门 */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Integer id) {
        return departmentService.delete(id)
                ? Result.success("删除成功")
                : Result.error("删除失败");
    }
}
