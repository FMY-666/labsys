package com.labsys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labsys.entity.OperLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface OperLogMapper extends BaseMapper<OperLog> {

    /** 日志表只增不减会一直涨，定时任务按保留天数删旧的 */
    @Delete("DELETE FROM sys_oper_log WHERE created_at < #{before}")
    int deleteBefore(@Param("before") LocalDateTime before);
}
