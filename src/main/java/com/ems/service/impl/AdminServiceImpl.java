package com.ems.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ems.entity.Admin;
import com.ems.mapper.AdminMapper;
import com.ems.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 管理员服务实现
 *
 * 【学习要点】
 * @Service：标记这是一个Service层的Bean，Spring会自动扫描并管理
 * @Autowired：自动注入依赖（AdminMapper会被自动装配到这里）
 *
 * LambdaQueryWrapper：MyBatis Plus提供的类型安全查询构造器
 * 比写SQL字符串安全（IDE会帮你检查字段名是否存在）
 */
@Service
public class AdminServiceImpl implements AdminService {

    @Autowired
    private AdminMapper adminMapper;

    @Override
    public Admin login(String username, String password) {
        // LambdaQueryWrapper：链式构建查询条件，类型安全
        LambdaQueryWrapper<Admin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Admin::getUsername, username)   // WHERE username = ?
               .eq(Admin::getPassword, password);   // AND password = ?
        return adminMapper.selectOne(wrapper);
    }

    @Override
    public boolean changePassword(Integer id, String oldPassword, String newPassword) {
        // 1. 先验证旧密码是否正确
        Admin admin = adminMapper.selectById(id);
        if (admin == null || !admin.getPassword().equals(oldPassword)) {
            return false;
        }
        // 2. 更新密码
        admin.setPassword(newPassword);
        return adminMapper.updateById(admin) > 0;
    }
}
