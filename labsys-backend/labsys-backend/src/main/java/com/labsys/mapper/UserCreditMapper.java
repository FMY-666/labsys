package com.labsys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labsys.entity.UserCredit;
import org.apache.ibatis.annotations.Mapper;

/** 只用 BaseMapper 的现成方法就够：按主键查、插入、按主键更新 */
@Mapper
public interface UserCreditMapper extends BaseMapper<UserCredit> {
}
