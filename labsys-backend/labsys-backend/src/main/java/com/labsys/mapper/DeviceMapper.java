package com.labsys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labsys.entity.Device;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    /**
     * 加排他行锁读设备。必须在外层事务里调用，否则语句一结束锁就放了。
     * 用途：把“查有没有冲突”和“插入预约”这两步合并成一个串行区，
     * 让同一台设备的并发预约在这里排队，而不是同时读到“没有冲突”。
     */
    @Select("SELECT * FROM device WHERE id = #{id} FOR UPDATE")
    Device selectByIdForUpdate(Long id);
}
