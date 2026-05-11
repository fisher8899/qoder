package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.context.DataScopeContext;
import com.ccerphr.assessment.dto.PermissionQueryDTO;
import com.ccerphr.assessment.entity.SysUser;
import com.ccerphr.assessment.entity.SysUserPermission;
import com.ccerphr.assessment.mapper.SysUserMapper;
import com.ccerphr.assessment.mapper.SysUserPermissionMapper;
import com.ccerphr.assessment.service.SysUserPermissionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SysUserPermissionServiceImpl extends ServiceImpl<SysUserPermissionMapper, SysUserPermission> implements SysUserPermissionService {

    private final SysUserMapper sysUserMapper;

    public SysUserPermissionServiceImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public PageResult<SysUserPermission> queryPage(PermissionQueryDTO query) {
        LambdaQueryWrapper<SysUserPermission> wrapper = new LambdaQueryWrapper<>();
        if (query.getUserId() != null) {
            wrapper.eq(SysUserPermission::getUserId, query.getUserId());
        }
        if (StringUtils.hasText(query.getUserName())) {
            wrapper.like(SysUserPermission::getUserName, query.getUserName());
        }
        if (StringUtils.hasText(query.getRoleCode())) {
            wrapper.eq(SysUserPermission::getRoleCode, query.getRoleCode());
        }

        // 数据范围过滤：通过关联 sys_user.unit_id/org_id 限制可见权限记录
        String dataScope = DataScopeContext.getDataScope();
        Long scopeId = DataScopeContext.getScopeId();
        if ("UNIT".equals(dataScope) && scopeId != null && scopeId != 0L) {
            LambdaQueryWrapper<SysUser> userQuery = new LambdaQueryWrapper<>();
            userQuery.eq(SysUser::getUnitId, scopeId).eq(SysUser::getDeleted, 0);
            userQuery.select(SysUser::getId);
            List<SysUser> unitUsers = sysUserMapper.selectList(userQuery);
            List<Long> userIds = unitUsers.stream().map(SysUser::getId).collect(Collectors.toList());
            if (!userIds.isEmpty()) {
                wrapper.in(SysUserPermission::getUserId, userIds);
            } else {
                wrapper.in(SysUserPermission::getUserId, Collections.singletonList(-1L));
            }
        } else if ("ORG".equals(dataScope) && scopeId != null && scopeId != 0L) {
            LambdaQueryWrapper<SysUser> userQuery = new LambdaQueryWrapper<>();
            userQuery.eq(SysUser::getOrgId, scopeId).eq(SysUser::getDeleted, 0);
            userQuery.select(SysUser::getId);
            List<SysUser> orgUsers = sysUserMapper.selectList(userQuery);
            List<Long> userIds = orgUsers.stream().map(SysUser::getId).collect(Collectors.toList());
            if (!userIds.isEmpty()) {
                wrapper.in(SysUserPermission::getUserId, userIds);
            } else {
                wrapper.in(SysUserPermission::getUserId, Collections.singletonList(-1L));
            }
        }

        wrapper.orderByDesc(SysUserPermission::getCreatedTime);
        Page<SysUserPermission> page = page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        // 填充登录用户名
        List<SysUserPermission> records = page.getRecords();
        if (!records.isEmpty()) {
            Set<Long> userIds = records.stream()
                    .map(SysUserPermission::getUserId)
                    .collect(Collectors.toSet());
            LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.in(SysUser::getId, userIds);
            userWrapper.select(SysUser::getId, SysUser::getUsername, SysUser::getRealName);
            List<SysUser> users = sysUserMapper.selectList(userWrapper);
            Map<Long, String> userIdToUsername = users.stream()
                    .collect(Collectors.toMap(SysUser::getId, SysUser::getUsername));
            Map<Long, String> userIdToRealName = users.stream()
                    .filter(u -> u.getRealName() != null)
                    .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName));
            records.forEach(r -> {
                r.setUsername(userIdToUsername.get(r.getUserId()));
                r.setUserName(userIdToRealName.get(r.getUserId()));
            });
        }
        PageResult<SysUserPermission> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(records);
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        return result;
    }

    @Override
    public SysUserPermission getByUserId(Long userId) {
        LambdaQueryWrapper<SysUserPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserPermission::getUserId, userId);
        return this.getOne(wrapper);
    }

    @Override
    public void addPermission(SysUserPermission permission) {
        if (permission.getUserId() == null) {
            throw new BusinessException("用户ID不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        permission.setCreatedTime(now);
        permission.setUpdatedTime(now);
        this.save(permission);
    }

    @Override
    public void updatePermission(SysUserPermission permission) {
        if (permission.getId() == null) {
            throw new BusinessException("权限ID不能为空");
        }
        permission.setUpdatedTime(LocalDateTime.now());
        this.updateById(permission);
    }

    @Override
    public void deletePermission(Long id) {
        if (!this.removeById(id)) {
            throw new BusinessException("权限不存在或已删除");
        }
    }
}
