package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.OrganizationQueryDTO;
import com.ccerphr.assessment.entity.SysEmployee;
import com.ccerphr.assessment.entity.SysLeader;
import com.ccerphr.assessment.entity.SysOrganization;
import com.ccerphr.assessment.entity.SysUnit;
import com.ccerphr.assessment.entity.SysUser;
import com.ccerphr.assessment.entity.SysUserPermission;
import com.ccerphr.assessment.mapper.SysEmployeeMapper;
import com.ccerphr.assessment.mapper.SysLeaderMapper;
import com.ccerphr.assessment.mapper.SysOrganizationMapper;
import com.ccerphr.assessment.mapper.SysUnitMapper;
import com.ccerphr.assessment.mapper.SysUserMapper;
import com.ccerphr.assessment.mapper.SysUserPermissionMapper;
import com.ccerphr.assessment.security.PasswordUtil;
import com.ccerphr.assessment.service.SysOrganizationService;
import com.ccerphr.assessment.util.DataScopeFilter;
import com.ccerphr.assessment.util.PinyinUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class SysOrganizationServiceImpl extends ServiceImpl<SysOrganizationMapper, SysOrganization> implements SysOrganizationService {

    private final SysEmployeeMapper sysEmployeeMapper;
    private final SysLeaderMapper sysLeaderMapper;
    private final SysUserMapper sysUserMapper;
    private final SysUserPermissionMapper sysUserPermissionMapper;
    private final SysUnitMapper sysUnitMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public SysOrganizationServiceImpl(SysEmployeeMapper sysEmployeeMapper,
                                      SysLeaderMapper sysLeaderMapper,
                                      SysUserMapper sysUserMapper,
                                      SysUserPermissionMapper sysUserPermissionMapper,
                                      SysUnitMapper sysUnitMapper) {
        this.sysEmployeeMapper = sysEmployeeMapper;
        this.sysLeaderMapper = sysLeaderMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysUserPermissionMapper = sysUserPermissionMapper;
        this.sysUnitMapper = sysUnitMapper;
    }

    @Override
    public PageResult<SysOrganization> queryPage(OrganizationQueryDTO query) {
        LambdaQueryWrapper<SysOrganization> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getOrgName())) {
            wrapper.like(SysOrganization::getOrgName, query.getOrgName());
        }
        if (StringUtils.hasText(query.getOrgType())) {
            wrapper.eq(SysOrganization::getOrgType, query.getOrgType());
        }
        if (query.getUnitId() != null) {
            wrapper.eq(SysOrganization::getUnitId, query.getUnitId());
        }
        DataScopeFilter.applyUnitFilter(wrapper, SysOrganization::getUnitId);
        wrapper.orderByAsc(SysOrganization::getSortCode);
        Page<SysOrganization> page = page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        PageResult<SysOrganization> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(page.getRecords());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        return result;
    }

    @Override
    public SysOrganization getDetail(Long id) {
        SysOrganization org = this.getById(id);
        if (org == null) {
            throw new BusinessException("考核组织不存在");
        }
        return org;
    }

    @Override
    public List<SysOrganization> getAll(Long unitId) {
        LambdaQueryWrapper<SysOrganization> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysOrganization::getIsEnabled, 1);
        if (unitId != null) {
            wrapper.eq(SysOrganization::getUnitId, unitId);
        } else {
            DataScopeFilter.applyUnitFilter(wrapper, SysOrganization::getUnitId);
        }
        wrapper.orderByAsc(SysOrganization::getSortCode);
        return this.list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addOrganization(SysOrganization organization) {
        Long unitId = DataScopeFilter.getAutoFillUnitId();
        if (unitId != null && organization.getUnitId() == null) {
            organization.setUnitId(unitId);
        }
        validateOrganizationName(organization);
        fillEmployeeNames(organization);
        LocalDateTime now = LocalDateTime.now();
        organization.setCreatedTime(now);
        organization.setUpdatedTime(now);
        this.save(organization);
        syncUserAndPermission(organization, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrganization(SysOrganization organization) {
        if (organization.getId() == null) {
            throw new BusinessException("考核组织ID不能为空");
        }
        SysOrganization oldOrg = this.getById(organization.getId());
        validateOrganizationName(organization);
        fillEmployeeNames(organization);
        organization.setUpdatedTime(LocalDateTime.now());
        this.updateById(organization);
        syncUserAndPermission(organization, oldOrg);
    }

    @Override
    public void deleteOrganization(Long id) {
        if (!this.removeById(id)) {
            throw new BusinessException("考核组织不存在或已删除");
        }
    }

    private void syncUserAndPermission(SysOrganization org, SysOrganization oldOrg) {
        processRoleAssignment(org, org.getDeptAdminId(),
                oldOrg != null ? oldOrg.getDeptAdminId() : null,
                "DEPT_ADMIN", "部门绩效管理员");

        processRoleAssignment(org, org.getDeptLeaderId(),
                oldOrg != null ? oldOrg.getDeptLeaderId() : null,
                "DEPT_LEADER", "部门负责人");

        processRoleAssignment(org, org.getSupervisorId(),
                oldOrg != null ? oldOrg.getSupervisorId() : null,
                "SUPERVISOR", "分管领导");
    }

    private void processRoleAssignment(SysOrganization org,
                                       Long newEmployeeId, Long oldEmployeeId,
                                       String roleCode, String roleName) {
        if (Objects.equals(newEmployeeId, oldEmployeeId)) {
            return;
        }

        if (oldEmployeeId != null) {
            removePermissionForEmployee(oldEmployeeId, roleCode, org.getId());
        }

        if (newEmployeeId != null) {
            ensureUserAndPermission(newEmployeeId, org, roleCode, roleName);
        }
    }

    private void ensureUserAndPermission(Long employeeId, SysOrganization org,
                                         String roleCode, String roleName) {
        SysEmployee employee = sysEmployeeMapper.selectById(employeeId);
        if (employee == null) {
            return;
        }

        SysUser user = findOrCreateUser(employee, org, roleCode, roleName);

        LambdaQueryWrapper<SysUserPermission> permWrapper = new LambdaQueryWrapper<>();
        permWrapper.eq(SysUserPermission::getUserId, user.getId())
                .eq(SysUserPermission::getRoleCode, roleCode)
                .eq(SysUserPermission::getDataScope, "ORG")
                .eq(SysUserPermission::getScopeId, org.getId())
                .eq(SysUserPermission::getDeleted, 0);
        long count = sysUserPermissionMapper.selectCount(permWrapper);

        if (count == 0) {
            SysUserPermission permission = new SysUserPermission();
            permission.setUserId(user.getId());
            permission.setUserName(employee.getEmployeeName());
            permission.setRoleCode(roleCode);
            permission.setDataScope("ORG");
            permission.setScopeId(org.getId());
            permission.setScopeName(org.getOrgName());
            permission.setStartDate(LocalDate.now());
            permission.setCreatedTime(LocalDateTime.now());
            permission.setUpdatedTime(LocalDateTime.now());
            permission.setDeleted(0);
            sysUserPermissionMapper.insert(permission);
        }
    }

    private SysUser findOrCreateUser(SysEmployee employee, SysOrganization org,
                                     String roleCode, String roleName) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getEmployeeId, employee.getId())
                .eq(SysUser::getDeleted, 0);
        SysUser existingUser = sysUserMapper.selectOne(wrapper);

        if (existingUser != null) {
            return existingUser;
        }

        SysUser newUser = new SysUser();
        String baseUsername = PinyinUtil.generateUsername(employee.getEmployeeName());
        String username = generateUniqueUsername(baseUsername);

        newUser.setUsername(username);
        newUser.setPassword(passwordEncoder.encode(PasswordUtil.generateTemporaryPassword()));
        newUser.setRealName(employee.getEmployeeName());
        newUser.setEmployeeId(employee.getId());
        newUser.setOrgId(org.getId());
        newUser.setOrgName(org.getOrgName());
        newUser.setUnitId(org.getUnitId());
        newUser.setRoleCode(roleCode);
        newUser.setRoleName(roleName);
        newUser.setIsEnabled(1);
        newUser.setDeleted(0);
        newUser.setCreatedTime(LocalDateTime.now());
        newUser.setUpdatedTime(LocalDateTime.now());
        sysUserMapper.insert(newUser);

        return newUser;
    }

    private String generateUniqueUsername(String baseUsername) {
        if (baseUsername == null || baseUsername.isEmpty()) {
            baseUsername = "user";
        }

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, baseUsername).eq(SysUser::getDeleted, 0);
        if (sysUserMapper.selectCount(wrapper) == 0) {
            return baseUsername;
        }

        for (int i = 1; i <= 99; i++) {
            String candidate = baseUsername + String.format("%02d", i);
            wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysUser::getUsername, candidate).eq(SysUser::getDeleted, 0);
            if (sysUserMapper.selectCount(wrapper) == 0) {
                return candidate;
            }
        }

        return baseUsername + System.currentTimeMillis();
    }

    private void removePermissionForEmployee(Long employeeId, String roleCode, Long orgId) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getEmployeeId, employeeId).eq(SysUser::getDeleted, 0);
        SysUser user = sysUserMapper.selectOne(wrapper);
        if (user == null) {
            return;
        }

        LambdaQueryWrapper<SysUserPermission> permWrapper = new LambdaQueryWrapper<>();
        permWrapper.eq(SysUserPermission::getUserId, user.getId())
                .eq(SysUserPermission::getRoleCode, roleCode)
                .eq(SysUserPermission::getDataScope, "ORG")
                .eq(SysUserPermission::getScopeId, orgId);
        sysUserPermissionMapper.delete(permWrapper);
    }

    private void fillEmployeeNames(SysOrganization org) {
        if (org.getDeptAdminId() != null) {
            SysEmployee emp = sysEmployeeMapper.selectById(org.getDeptAdminId());
            if (emp != null) {
                org.setDeptAdminName(emp.getEmployeeName());
            }
        }
        if (org.getDeptLeaderId() != null) {
            SysEmployee emp = sysEmployeeMapper.selectById(org.getDeptLeaderId());
            if (emp != null) {
                org.setDeptLeaderName(emp.getEmployeeName());
            }
        }
        if (org.getSupervisorId() != null) {
            SysLeader leader = sysLeaderMapper.selectById(org.getSupervisorId());
            if (leader != null) {
                org.setSupervisorName(leader.getLeaderName());
            }
        }
        if (org.getAssessorId() != null) {
            SysEmployee emp = sysEmployeeMapper.selectById(org.getAssessorId());
            if (emp != null) {
                org.setAssessorName(emp.getEmployeeName());
            }
        }
    }

    private void validateOrganizationName(SysOrganization organization) {
        if (!StringUtils.hasText(organization.getOrgName())) {
            return;
        }

        String orgName = organization.getOrgName().trim();

        LambdaQueryWrapper<SysUnit> unitWrapper = new LambdaQueryWrapper<>();
        unitWrapper.eq(SysUnit::getUnitName, orgName)
                .eq(SysUnit::getDeleted, 0);
        if (sysUnitMapper.selectCount(unitWrapper) > 0) {
            throw new BusinessException("考核组织名称不能与单位名称重复，请录入该单位下的具体部门名称");
        }

        LambdaQueryWrapper<SysOrganization> orgWrapper = new LambdaQueryWrapper<>();
        orgWrapper.eq(SysOrganization::getOrgName, orgName)
                .eq(SysOrganization::getDeleted, 0);
        if (organization.getId() != null) {
            orgWrapper.ne(SysOrganization::getId, organization.getId());
        }
        if (this.baseMapper.selectCount(orgWrapper) > 0) {
            throw new BusinessException("考核组织名称已存在");
        }
    }
}
