package com.labsys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labsys.entity.UserCredit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 除了解禁需要显式写 NULL，其余用 BaseMapper 的现成方法就够 */
@Mapper
public interface UserCreditMapper extends BaseMapper<UserCredit> {

    /**
     * 把禁约时间显式置空。
     * 不能指望 updateById —— MyBatis-Plus 会跳过值为 null 的字段，
     * 解禁时会变成“违规清零了，但数据库里的 banned_until 还在，人还是约不了”。
     */
    @Update("UPDATE sys_user_credit SET banned_until = NULL WHERE user_id = #{userId}")
    int clearBanUntil(@Param("userId") Long userId);
}
