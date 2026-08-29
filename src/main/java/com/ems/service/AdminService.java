package com.ems.service;

import com.ems.entity.Admin;

/**
 * 管理员服务接口
 *
 * 【学习要点——为什么要有Service层？】
 * Mapper 只负责数据库操作（一个Mapper对应一张表）。
 * Service 负责业务逻辑（可能涉及多张表、事务、缓存等）。
 * Controller 只负责接收请求和返回响应。
 *
 * 这就是经典的三层架构：Controller → Service → Mapper
 */
public interface AdminService {

    /**
     * 登录验证
     * @return 验证通过返回Admin对象，失败返回null
     */
    Admin login(String username, String password);

    /**
     * 修改密码
     */
    boolean changePassword(Integer id, String oldPassword, String newPassword);
}
