package com.ccerphr.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ccerphr.assessment.entity.SysUserPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserPermissionMapper extends BaseMapper<SysUserPermission> {

    /**
     * 查询用户当前有效的权限列表
     * 有效条件：deleted=0，且 start_date <= 当前日期，且 (end_date IS NULL 或 end_date >= 当前日期)
     */
    @Select("SELECT * FROM sys_user_permission WHERE user_id = #{userId} AND deleted = 0 " +
            "AND start_date <= CURDATE() AND (end_date IS NULL OR end_date >= CURDATE())")
    List<SysUserPermission> selectActiveByUserId(Long userId);
}
