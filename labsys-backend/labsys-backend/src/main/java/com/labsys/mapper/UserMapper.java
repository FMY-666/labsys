package com.labsys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labsys.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    User findByUsername(String username);

}
