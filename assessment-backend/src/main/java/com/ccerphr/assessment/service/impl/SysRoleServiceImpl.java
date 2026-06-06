package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.RoleMenuDTO;
import com.ccerphr.assessment.dto.RoleMenuItemDTO;
import com.ccerphr.assessment.entity.SysMenu;
import com.ccerphr.assessment.entity.SysRole;
import com.ccerphr.assessment.entity.SysRoleMenu;
import com.ccerphr.assessment.entity.SysUserPermission;
import com.ccerphr.assessment.mapper.SysMenuMapper;
import com.ccerphr.assessment.mapper.SysRoleMapper;
import com.ccerphr.assessment.mapper.SysRoleMenuMapper;
import com.ccerphr.assessment.mapper.SysUserPermissionMapper;
import com.ccerphr.assessment.service.SysMenuService;
import com.ccerphr.assessment.service.SysRoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysUserPermissionMapper sysUserPermissionMapper;

    public SysRoleServiceImpl(SysRoleMenuMapper sysRoleMenuMapper,
                              SysMenuMapper sysMenuMapper,
                              SysUserPermissionMapper sysUserPermissionMapper) {
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.sysUserPermissionMapper = sysUserPermissionMapper;
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
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        SysRole role = this.getById(id);
        if (role == null) {
            throw new BusinessException("职责不存在或已删除");
        }
        if (StringUtils.hasText(role.getRoleCode())) {
            long inUse = sysUserPermissionMapper.selectCount(
                new LambdaQueryWrapper<SysUserPermission>().eq(SysUserPermission::getRoleCode, role.getRoleCode())
            );
            if (inUse > 0) {
                throw new BusinessException("该职责已被用户引用，无法删除");
            }
        }
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
        validateAssignableMenus(role, roleMenuItems);

        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleMenu::getRoleId, roleId);
        sysRoleMenuMapper.delete(wrapper);

        if (roleMenuItems == null || roleMenuItems.isEmpty()) {
            return;
        }

        List<SysRoleMenu> roleMenus = roleMenuItems.stream()
            .filter(item -> item.getMenuId() != null && item.getMenuId() > 0)
            .collect(Collectors.toMap(RoleMenuItemDTO::getMenuId, item -> item, (left, right) -> left, LinkedHashMap::new))
            .values()
            .stream()
            .map(item -> {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(item.getMenuId());
                rm.setSortCode(item.getSortCode() != null ? item.getSortCode() : 0);
                return rm;
            })
            .collect(Collectors.toList());

        for (SysRoleMenu rm : roleMenus) {
            sysRoleMenuMapper.insert(rm);
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

    private void validateAssignableMenus(SysRole role, List<RoleMenuItemDTO> roleMenuItems) {
        if (roleMenuItems == null || roleMenuItems.isEmpty()) {
            return;
        }

        List<Long> menuIds = roleMenuItems.stream()
            .map(RoleMenuItemDTO::getMenuId)
            .filter(id -> id != null && id > 0)
            .distinct()
            .collect(Collectors.toList());
        if (menuIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<SysMenu> menuWrapper = new LambdaQueryWrapper<>();
        menuWrapper.in(SysMenu::getId, menuIds).eq(SysMenu::getIsEnabled, 1);
        List<SysMenu> menus = sysMenuMapper.selectList(menuWrapper);
        if (menus.size() != menuIds.size()) {
            throw new BusinessException("存在无效或已停用的菜单");
        }

        if ("SYSTEM".equals(role.getRoleType())) {
            return;
        }

        List<String> invalidMenus = menus.stream()
            .filter(menu -> StringUtils.hasText(menu.getMenuPath()))
            .filter(menu -> !SysMenuServiceImpl.isMenuAllowedForRoleType(menu, role.getRoleType()))
            .map(SysMenu::getMenuName)
            .collect(Collectors.toList());
        if (!invalidMenus.isEmpty()) {
            throw new BusinessException("职责类型[" + role.getRoleType() + "]不能分配菜单: " + String.join("、", invalidMenus));
        }
    }
}
