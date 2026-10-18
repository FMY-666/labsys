package com.labsys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labsys.entity.UserToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserTokenMapper extends BaseMapper<UserToken> {

    @Select("SELECT * FROM sys_token WHERE token = #{token}")
    UserToken findByToken(String token);
}
