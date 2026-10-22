package com.labsys.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * sys_user_role / sys_role_menu 这两张中间表没有实体（只有两个 id，做实体没意义），
 * 所以直接写 SQL。授权是“整组替换”语义：先删掉这个人/角色的全部授权，再批量插入新的。
 * 不逐条 diff —— 授权条目只有十几行，全量替换比算增量简单，也不会漏删。
 */
@Mapper
public interface GrantMapper {

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteUserRoles(@Param("userId") Long userId);

    @Insert("<script>INSERT INTO sys_user_role (user_id, role_id) VALUES " +
            "<foreach collection='roleIds' item='rid' separator=','>(#{userId}, #{rid})</foreach></script>")
    int insertUserRoles(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int deleteRoleMenus(@Param("roleId") Long roleId);

    @Insert("<script>INSERT INTO sys_role_menu (role_id, menu_id) VALUES " +
            "<foreach collection='menuIds' item='mid' separator=','>(#{roleId}, #{mid})</foreach></script>")
    int insertRoleMenus(@Param("roleId") Long roleId, @Param("menuIds") List<Long> menuIds);
}
