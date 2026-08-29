package com.ems.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ems.entity.Admin;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理员 Mapper
 *
 * 【学习要点——MyBatis Plus 的 BaseMapper】
 * 继承 BaseMapper<Admin> 之后，自动拥有以下方法（无需写任何SQL！）：
 * - insert(Admin)          新增
 * - deleteById(1)          按ID删除
 * - updateById(Admin)      按ID更新
 * - selectById(1)          按ID查询
 * - selectList(wrapper)    条件查询列表
 * - selectPage(page, wrapper) 分页查询
 *
 * 如果BaseMapper不够用，可以在这里自定义方法，然后在XML中写SQL。
 * 这就是 MyBatis Plus 比原版 MyBatis 强大的地方！
 */
@Mapper
public interface AdminMapper extends BaseMapper<Admin> {

    // 不需要写任何代码！BaseMapper已经提供了全部CRUD方法。
    // 如果需要自定义查询，在这里加方法签名，在XML中写SQL。
}
