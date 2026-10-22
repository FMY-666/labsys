package com.labsys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labsys.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    // 一个用户可能有多个角色，所以返回列表而不是单个字符串
    @Select("SELECT r.role_code FROM sys_user_role ur " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<String> findRoleCodesByUserId(Long userId);

    // 同一页面可能被多个角色同时授予，DISTINCT 去重；目录节点没有路由，按 path 为空排除
    @Select("SELECT DISTINCT m.path FROM sys_user_role ur " +
            "JOIN sys_role_menu rm ON rm.role_id = ur.role_id " +
            "JOIN sys_menu m ON m.id = rm.menu_id " +
            "WHERE ur.user_id = #{userId} AND m.path IS NOT NULL")
    List<String> findMenuPathsByUserId(Long userId);

    /**
     * 批量查一批用户的角色，给用户列表用。
     * 一个用户多个角色，所以返回的是“扁平的 (用户, 角色) 行”，在 Service 里按用户分组 ——
     * 一条 SQL 查完 N 个用户的角色，不要按用户循环查（N+1 次查询）。
     */
    @Select("<script>" +
            "SELECT ur.user_id AS userId, r.id AS roleId, r.role_code AS roleCode, r.role_name AS roleName " +
            "FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE ur.user_id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<Map<String, Object>> findRolesByUserIds(@Param("ids") List<Long> ids);

    /** 所有角色的授权关系，一次性取回再分组，3 个角色没必要逐个查 */
    @Select("SELECT role_id AS roleId, menu_id AS menuId FROM sys_role_menu")
    List<Map<String, Object>> findAllRoleMenus();

    /** 当前还有几个启用的管理员 —— 防呆用：最后一个管理员不能被停用、也不能被摘掉 ADMIN */
    @Select("SELECT COUNT(*) FROM sys_user_role ur " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "JOIN sys_user u ON u.id = ur.user_id " +
            "WHERE r.role_code = 'ADMIN' AND u.enabled = 1")
    long countEnabledAdmins();
}
