package com.ccerphr.assessment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.UserQueryDTO;
import com.ccerphr.assessment.entity.SysUser;

public interface SysUserService extends IService<SysUser> {

    PageResult<SysUser> listByPage(UserQueryDTO query);

    SysUser getDetail(Long id);

    void add(SysUser user);

    void update(SysUser user);

    void toggleEnabled(Long id);

    void resetPassword(Long id);

    void delete(Long id);
}
