package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.RoleMenuDTO;
import com.ccerphr.assessment.dto.RoleMenuItemDTO;
import com.ccerphr.assessment.entity.SysRole;
import com.ccerphr.assessment.entity.SysRoleMenu;
import com.ccerphr.assessment.mapper.SysRoleMapper;
import com.ccerphr.assessment.mapper.SysRoleMenuMapper;
import com.ccerphr.assessment.service.SysRoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysRoleMenuMapper sysRoleMenuMapper;

    public SysRoleServiceImpl(SysRoleMenuMapper sysRoleMenuMapper) {
        this.sysRoleMenuMapper = sysRoleMenuMapper;
    }

    @Override
    public PageResult<SysRole> queryPage(String roleName, long current, long size) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(roleName)) {
            wrapper.like(SysRole::getRoleName, roleName);
        }
        wrapper.orderByDesc(SysRole::getCreatedTime);
        Page<SysRole> page = page(new Page<>(current, size), wrapper);
        PageResult<SysRole> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(page.getRecords());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        return result;
    }

    @Override
    public RoleMenuDTO getDetail(Long id) {
        SysRole role = this.getById(id);
        if (role == null) {
            throw new BusinessException("职责不存在");
        }
        RoleMenuDTO dto = new RoleMenuDTO();
        dto.setId(role.getId());
        dto.setRoleName(role.getRoleName());
        dto.setRoleCode(role.getRoleCode());
        dto.setDescription(role.getDescription());
        dto.setMenuIds(getRoleMenuIds(id));
        return dto;
    }

    @Override
    public void addRole(SysRole role) {
        role.setCreatedTime(LocalDateTime.now());
        this.save(role);
    }

    @Override
    public void updateRole(SysRole role) {
        if (role.getId() == null) {
            throw new BusinessException("职责ID不能为空");
        }
        this.updateById(role);
    }

    @Override
    public void deleteRole(Long id) {
        if (!this.removeById(id)) {
            throw new BusinessException("职责不存在或已删除");
        }
        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleMenu::getRoleId, id);
        sysRoleMenuMapper.delete(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<RoleMenuItemDTO> roleMenuItems) {
        SysRole role = this.getById(roleId);
        if (role == null) {
            throw new BusinessException("职责不存在");
        }
        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleMenu::getRoleId, roleId);
        sysRoleMenuMapper.delete(wrapper);
        if (roleMenuItems != null && !roleMenuItems.isEmpty()) {
            List<SysRoleMenu> roleMenus = roleMenuItems.stream().map(item -> {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(item.getMenuId());
                rm.setSortCode(item.getSortCode() != null ? item.getSortCode() : 0);
                return rm;
            }).collect(Collectors.toList());
            for (SysRoleMenu rm : roleMenus) {
                sysRoleMenuMapper.insert(rm);
            }
        }
    }

    @Override
    public List<Long> getRoleMenuIds(Long roleId) {
        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleMenu::getRoleId, roleId);
        List<SysRoleMenu> list = sysRoleMenuMapper.selectList(wrapper);
        return list.stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
    }

    @Override
    public List<RoleMenuItemDTO> getRoleMenuDetails(Long roleId) {
        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleMenu::getRoleId, roleId);
        wrapper.orderByAsc(SysRoleMenu::getSortCode);
        List<SysRoleMenu> list = sysRoleMenuMapper.selectList(wrapper);
        return list.stream().map(rm -> {
            RoleMenuItemDTO dto = new RoleMenuItemDTO();
            dto.setMenuId(rm.getMenuId());
            dto.setSortCode(rm.getSortCode());
            return dto;
        }).collect(Collectors.toList());
    }
}
