package com.labsys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labsys.entity.TicketLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TicketLogMapper extends BaseMapper<TicketLog> {

    /** 一个工单的完整处理历史，按时间正序 —— 读起来像一条时间线 */
    @Select("SELECT * FROM ticket_log WHERE ticket_id = #{ticketId} ORDER BY id")
    List<TicketLog> findByTicketId(Long ticketId);
}
