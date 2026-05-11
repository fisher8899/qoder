package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.OrganizationQueryDTO;
import com.ccerphr.assessment.entity.SysEmployee;
import com.ccerphr.assessment.entity.SysOrganization;
import com.ccerphr.assessment.entity.SysUser;
import com.ccerphr.assessment.entity.SysUserPermission;
import com.ccerphr.assessment.entity.SysLeader;
import com.ccerphr.assessment.mapper.SysEmployeeMapper;
import com.ccerphr.assessment.mapper.SysLeaderMapper;
import com.ccerphr.assessment.mapper.SysOrganizationMapper;
import com.ccerphr.assessment.mapper.SysUserMapper;
import com.ccerphr.assessment.mapper.SysUserPermissionMapper;
import com.ccerphr.assessment.service.SysOrganizationService;
import com.ccerphr.assessment.util.DataScopeFilter;
import com.ccerphr.assessment.util.PinyinUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
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
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public SysOrganizationServiceImpl(SysEmployeeMapper sysEmployeeMapper,
                                      SysLeaderMapper sysLeaderMapper,
                                      SysUserMapper sysUserMapper,
                                      SysUserPermissionMapper sysUserPermissionMapper) {
        this.sysEmployeeMapper = sysEmployeeMapper;
        this.sysLeaderMapper = sysLeaderMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysUserPermissionMapper = sysUserPermissionMapper;
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
        // 数据范围过滤 - 按 unit_id 过滤
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
    public List<SysOrganization> getAll() {
        LambdaQueryWrapper<SysOrganization> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysOrganization::getIsEnabled, 1);
        // 数据范围过滤
        DataScopeFilter.applyUnitFilter(wrapper, SysOrganization::getUnitId);
        wrapper.orderByAsc(SysOrganization::getSortCode);
        return this.list(wrapper);
    }

    @Override
    public void addOrganization(SysOrganization organization) {
        // 自动填入 unitId
        Long unitId = DataScopeFilter.getAutoFillUnitId();
        if (unitId != null && organization.getUnitId() == null) {
            organization.setUnitId(unitId);
        }
        fillEmployeeNames(organization);
        LocalDateTime now = LocalDateTime.now();
        organization.setCreatedTime(now);
        organization.setUpdatedTime(now);
        this.save(organization);
        // 新增时oldOrg为null，直接为新人员创建用户+分配权限
        syncUserAndPermission(organization, null);
    }

    @Override
    public void updateOrganization(SysOrganization organization) {
        if (organization.getId() == null) {
            throw new BusinessException("考核组织ID不能为空");
        }
        // 先查出旧数据，用于比较人员变更
        SysOrganization oldOrg = this.getById(organization.getId());
        fillEmployeeNames(organization);
        organization.setUpdatedTime(LocalDateTime.now());
        this.updateById(organization);
        // 比较新旧值处理角色变更
        syncUserAndPermission(organization, oldOrg);
    }

    @Override
    public void deleteOrganization(Long id) {
        if (!this.removeById(id)) {
            throw new BusinessException("考核组织不存在或已删除");
        }
    }

    // ==================== 自动创建用户+分配权限 核心逻辑 ====================

    /**
     * 保存组织时同步用户和权限
     * @param org 当前组织
     * @param oldOrg 修改前的组织（新增时为null）
     */
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

    /**
     * 处理单个角色的分配：比较新旧人员，旧人员删除权限，新人员创建用户+分配权限
     */
    private void processRoleAssignment(SysOrganization org,
                                       Long newEmployeeId, Long oldEmployeeId,
                                       String roleCode, String roleName) {
        // 没有变化则跳过
        if (Objects.equals(newEmployeeId, oldEmployeeId)) {
            return;
        }

        // 旧人员：删除该角色+该部门的权限
        if (oldEmployeeId != null) {
            removePermissionForEmployee(oldEmployeeId, roleCode, org.getId());
        }

        // 新人员：创建用户+分配权限
        if (newEmployeeId != null) {
            ensureUserAndPermission(newEmployeeId, org, roleCode, roleName);
        }
    }

    /**
     * 确保员工有用户账号并分配权限
     */
    private void ensureUserAndPermission(Long employeeId, SysOrganization org,
                                         String roleCode, String roleName) {
        // 1. 查员工信息
        SysEmployee employee = sysEmployeeMapper.selectById(employeeId);
        if (employee == null) return;

        // 2. 查或创建用户
        SysUser user = findOrCreateUser(employee, org, roleCode, roleName);

        // 3. 检查权限是否已存在（避免重复创建）
        LambdaQueryWrapper<SysUserPermission> permWrapper = new LambdaQueryWrapper<>();
        permWrapper.eq(SysUserPermission::getUserId, user.getId())
                .eq(SysUserPermission::getRoleCode, roleCode)
                .eq(SysUserPermission::getDataScope, "ORG")
                .eq(SysUserPermission::getScopeId, org.getId())
                .eq(SysUserPermission::getDeleted, 0);
        long count = sysUserPermissionMapper.selectCount(permWrapper);

        if (count == 0) {
            // 4. 创建权限记录
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

    /**
     * 查找已有用户或创建新用户
     */
    private SysUser findOrCreateUser(SysEmployee employee, SysOrganization org,
                                     String roleCode, String roleName) {
        // 查找已有用户
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getEmployeeId, employee.getId())
                .eq(SysUser::getDeleted, 0);
        SysUser existingUser = sysUserMapper.selectOne(wrapper);

        if (existingUser != null) {
            return existingUser;
        }

        // 创建新用户
        SysUser newUser = new SysUser();
        String baseUsername = PinyinUtil.generateUsername(employee.getEmployeeName());
        String username = generateUniqueUsername(baseUsername);

        newUser.setUsername(username);
        newUser.setPassword(passwordEncoder.encode("123456"));
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

    /**
     * 生成不重复的用户名，重复时加数字后缀 01, 02...
     */
    private String generateUniqueUsername(String baseUsername) {
        if (baseUsername == null || baseUsername.isEmpty()) {
            baseUsername = "user";
        }
        // 检查基础用户名是否可用
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, baseUsername).eq(SysUser::getDeleted, 0);
        if (sysUserMapper.selectCount(wrapper) == 0) {
            return baseUsername;
        }

        // 加数字后缀
        for (int i = 1; i <= 99; i++) {
            String candidate = baseUsername + String.format("%02d", i);
            wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysUser::getUsername, candidate).eq(SysUser::getDeleted, 0);
            if (sysUserMapper.selectCount(wrapper) == 0) {
                return candidate;
            }
        }

        // fallback：使用时间戳
        return baseUsername + System.currentTimeMillis();
    }

    /**
     * 删除旧人员在该组织的该角色权限记录
     */
    private void removePermissionForEmployee(Long employeeId, String roleCode, Long orgId) {
        // 找到该员工对应的用户
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getEmployeeId, employeeId).eq(SysUser::getDeleted, 0);
        SysUser user = sysUserMapper.selectOne(wrapper);
        if (user == null) return;

        // 删除该用户在该部门的该角色权限（@TableLogic 自动变逻辑删除）
        LambdaQueryWrapper<SysUserPermission> permWrapper = new LambdaQueryWrapper<>();
        permWrapper.eq(SysUserPermission::getUserId, user.getId())
                .eq(SysUserPermission::getRoleCode, roleCode)
                .eq(SysUserPermission::getDataScope, "ORG")
                .eq(SysUserPermission::getScopeId, orgId);
        sysUserPermissionMapper.delete(permWrapper);
    }

    // ==================== 辅助方法 ====================

    /**
     * 当deptAdminId/deptLeaderId/supervisorId/assessorId传入时，
     * 从sys_employee表获取对应的人员姓名自动填充
     */
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
}
