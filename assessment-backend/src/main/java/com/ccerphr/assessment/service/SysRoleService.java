package com.ccerphr.assessment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.RoleMenuDTO;
import com.ccerphr.assessment.dto.RoleMenuItemDTO;
import com.ccerphr.assessment.entity.SysRole;

import java.util.List;

public interface SysRoleService extends IService<SysRole> {

    PageResult<SysRole> queryPage(String roleName, long current, long size);

    RoleMenuDTO getDetail(Long id);

    void addRole(SysRole role);

    void updateRole(SysRole role);

    void deleteRole(Long id);

    void assignMenus(Long roleId, List<RoleMenuItemDTO> roleMenuItems);

    List<Long> getRoleMenuIds(Long roleId);

    List<RoleMenuItemDTO> getRoleMenuDetails(Long roleId);
}
