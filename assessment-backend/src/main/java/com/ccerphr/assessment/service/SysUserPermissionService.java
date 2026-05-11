package com.ccerphr.assessment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.PermissionQueryDTO;
import com.ccerphr.assessment.entity.SysUserPermission;

public interface SysUserPermissionService extends IService<SysUserPermission> {

    PageResult<SysUserPermission> queryPage(PermissionQueryDTO query);

    SysUserPermission getByUserId(Long userId);

    void addPermission(SysUserPermission permission);

    void updatePermission(SysUserPermission permission);

    void deletePermission(Long id);
}
