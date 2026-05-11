package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.UnitQueryDTO;
import com.ccerphr.assessment.entity.*;
import com.ccerphr.assessment.mapper.*;
import com.ccerphr.assessment.service.SysUnitService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class SysUnitServiceImpl extends ServiceImpl<SysUnitMapper, SysUnit> implements SysUnitService {

    private final SysEmployeeMapper sysEmployeeMapper;
    private final SysLeaderMapper sysLeaderMapper;
    private final SysOrganizationMapper sysOrganizationMapper;
    private final SysUserMapper sysUserMapper;
    private final SysUserPermissionMapper sysUserPermissionMapper;

    public SysUnitServiceImpl(SysEmployeeMapper sysEmployeeMapper,
                              SysLeaderMapper sysLeaderMapper,
                              SysOrganizationMapper sysOrganizationMapper,
                              SysUserMapper sysUserMapper,
                              SysUserPermissionMapper sysUserPermissionMapper) {
        this.sysEmployeeMapper = sysEmployeeMapper;
        this.sysLeaderMapper = sysLeaderMapper;
        this.sysOrganizationMapper = sysOrganizationMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysUserPermissionMapper = sysUserPermissionMapper;
    }

    @Override
    public PageResult<SysUnit> queryPage(UnitQueryDTO query) {
        LambdaQueryWrapper<SysUnit> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getUnitName())) {
            wrapper.like(SysUnit::getUnitName, query.getUnitName());
        }
        if (StringUtils.hasText(query.getUnitType())) {
            wrapper.eq(SysUnit::getUnitType, query.getUnitType());
        }
        if (query.getIsEnabled() != null) {
            wrapper.eq(SysUnit::getIsEnabled, query.getIsEnabled());
        }
        wrapper.orderByDesc(SysUnit::getCreatedTime);
        Page<SysUnit> page = page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        PageResult<SysUnit> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(page.getRecords());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        return result;
    }

    @Override
    public SysUnit getDetail(Long id) {
        SysUnit unit = this.getById(id);
        if (unit == null) {
            throw new BusinessException("单位不存在");
        }
        return unit;
    }

    @Override
    public void addUnit(SysUnit unit) {
        LocalDateTime now = LocalDateTime.now();
        unit.setCreatedTime(now);
        unit.setUpdatedTime(now);
        this.save(unit);
    }

    @Override
    public void updateUnit(SysUnit unit) {
        if (unit.getId() == null) {
            throw new BusinessException("单位ID不能为空");
        }
        unit.setUpdatedTime(LocalDateTime.now());
        this.updateById(unit);
    }

    @Override
    public void toggleStatus(Long id) {
        SysUnit unit = this.getById(id);
        if (unit == null) {
            throw new BusinessException("单位不存在");
        }
        unit.setIsEnabled(unit.getIsEnabled() != null && unit.getIsEnabled() == 1 ? 0 : 1);
        unit.setUpdatedTime(LocalDateTime.now());
        this.updateById(unit);
    }

    @Override
    public void deleteUnit(Long id) {
        SysUnit unit = this.getById(id);
        if (unit == null) {
            throw new BusinessException("单位不存在");
        }

        // 检查 sys_employee 是否引用
        long empCount = sysEmployeeMapper.selectCount(
                new LambdaQueryWrapper<SysEmployee>().eq(SysEmployee::getUnitId, id));
        if (empCount > 0) {
            throw new BusinessException("该数据被引用，不可删除，请失效该记录");
        }

        // 检查 sys_leader 是否引用
        long leaderCount = sysLeaderMapper.selectCount(
                new LambdaQueryWrapper<SysLeader>().eq(SysLeader::getUnitId, id));
        if (leaderCount > 0) {
            throw new BusinessException("该数据被引用，不可删除，请失效该记录");
        }

        // 检查 sys_organization 是否引用
        long orgCount = sysOrganizationMapper.selectCount(
                new LambdaQueryWrapper<SysOrganization>().eq(SysOrganization::getUnitId, id));
        if (orgCount > 0) {
            throw new BusinessException("该数据被引用，不可删除，请失效该记录");
        }

        // 检查 sys_user 是否引用
        long userCount = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUnitId, id));
        if (userCount > 0) {
            throw new BusinessException("该数据被引用，不可删除，请失效该记录");
        }

        // 检查 sys_user_permission 是否引用
        long permCount = sysUserPermissionMapper.selectCount(
                new LambdaQueryWrapper<SysUserPermission>().eq(SysUserPermission::getUnitScope, unit.getUnitName()));
        if (permCount > 0) {
            throw new BusinessException("该数据被引用，不可删除，请失效该记录");
        }

        // 无引用，执行逻辑删除
        this.removeById(id);
    }
}
