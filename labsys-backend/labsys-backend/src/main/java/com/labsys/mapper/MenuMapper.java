package com.labsys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labsys.entity.Menu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MenuMapper extends BaseMapper<Menu> {

    // 只取当前用户通过角色拿到的菜单，停用的（visible = 0）不返回
    @Select("SELECT DISTINCT m.id, m.parent_id, m.menu_name, m.path, m.sort_order, m.visible " +
            "FROM sys_user_role ur " +
            "JOIN sys_role_menu rm ON rm.role_id = ur.role_id " +
            "JOIN sys_menu m ON m.id = rm.menu_id " +
            "WHERE ur.user_id = #{userId} AND m.visible = 1 " +
            "ORDER BY m.sort_order, m.id")
    List<Menu> findByUserId(Long userId);

    /** 全部菜单，给角色管理页的授权勾选框用（不按用户过滤） */
    @Select("SELECT id, parent_id, menu_name, path, sort_order, visible " +
            "FROM sys_menu WHERE visible = 1 ORDER BY sort_order, id")
    List<Menu> findAll();
}
