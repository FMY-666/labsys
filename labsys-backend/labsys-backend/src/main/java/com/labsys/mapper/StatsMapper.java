package com.labsys.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 看板统计用的查询。
 * 都是聚合查询，BaseMapper 的现成方法做不了，所以手写 SQL。
 * 返回 Map 是因为结果形状各不相同（有 key-count、有 name-count、有日期-count），
 * 特意为每种图建一个 DTO 不划算。
 */
@Mapper
public interface StatsMapper {

    /** 设备状态分布：AVAILABLE 多少台、BORROWED 多少台…… */
    @Select("SELECT status AS name, COUNT(*) AS value FROM device GROUP BY status")
    List<Map<String, Object>> deviceStatusCount();

    /** 借用排行：被借过次数最多的设备 */
    @Select("SELECT d.name AS name, COUNT(*) AS value " +
            "FROM reservation r JOIN device d ON d.id = r.device_id " +
            "WHERE r.status IN ('BORROWED','RETURNED','OVERDUE') " +
            "GROUP BY r.device_id, d.name ORDER BY value DESC LIMIT 5")
    List<Map<String, Object>> borrowRanking();

    /** 故障 TOP：报修次数最多的设备 */
    @Select("SELECT d.name AS name, COUNT(*) AS value " +
            "FROM ticket t JOIN device d ON d.id = t.device_id " +
            "GROUP BY t.device_id, d.name ORDER BY value DESC LIMIT 5")
    List<Map<String, Object>> faultRanking();

    /**
     * 工单趋势：最近 N 天每天新增多少工单（没有工单的日期不会出现，前端自己补 0）
     * 注意 GROUP BY 必须和 SELECT 的表达式完全一致：MySQL 开着 only_full_group_by 时，
     * SELECT 写 DATE_FORMAT、GROUP BY 写 DATE 会被判为“非聚合列不在分组里”而直接报错。
     * 用别名分组最省事，ORDER BY 再用 MIN(created_at) 保住时间顺序。
     */
    @Select("SELECT DATE_FORMAT(created_at, '%m-%d') AS name, COUNT(*) AS value " +
            "FROM ticket WHERE created_at >= #{since} " +
            "GROUP BY name ORDER BY MIN(created_at)")
    List<Map<String, Object>> ticketTrend(@Param("since") LocalDateTime since);

    /** 工单按状态分布 */
    @Select("SELECT status AS name, COUNT(*) AS value FROM ticket GROUP BY status")
    List<Map<String, Object>> ticketStatusCount();
}
