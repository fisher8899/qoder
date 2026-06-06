package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.UserQueryDTO;
import com.ccerphr.assessment.entity.SysEmployee;
import com.ccerphr.assessment.entity.SysUser;
import com.ccerphr.assessment.mapper.SysEmployeeMapper;
import com.ccerphr.assessment.mapper.SysUserMapper;
import com.ccerphr.assessment.security.PasswordUtil;
import com.ccerphr.assessment.service.SysUserService;
import com.ccerphr.assessment.util.DataScopeFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private static final String DEFAULT_ROLE_CODE = "USER";

    private final SysEmployeeMapper employeeMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public SysUserServiceImpl(SysEmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    @Override
    public PageResult<SysUser> listByPage(UserQueryDTO query) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(SysUser::getRealName, query.getKeyword())
                .or().like(SysUser::getUsername, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getRoleCode())) {
            wrapper.eq(SysUser::getRoleCode, query.getRoleCode());
        }
        if (query.getIsEnabled() != null) {
            wrapper.eq(SysUser::getIsEnabled, query.getIsEnabled());
        }
        DataScopeFilter.applyUnitFilter(wrapper, SysUser::getUnitId);
        wrapper.orderByDesc(SysUser::getCreatedTime);

        Page<SysUser> page = page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        PageResult<SysUser> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(page.getRecords());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        return result;
    }

    @Override
    public SysUser getDetail(Long id) {
        SysUser user = this.getById(id);
        if (user == null) {
            throw new BusinessException("User does not exist");
        }
        return user;
    }

    @Override
    public void add(SysUser user) {
        Long count = this.lambdaQuery().eq(SysUser::getUsername, user.getUsername()).count();
        if (count > 0) {
            throw new BusinessException("Username already exists");
        }
        if (!StringUtils.hasText(user.getRoleCode())) {
            user.setRoleCode(DEFAULT_ROLE_CODE);
            user.setRoleName("USER");
        }
        if (user.getEmployeeId() != null) {
            SysEmployee employee = employeeMapper.selectById(user.getEmployeeId());
            if (employee != null) {
                user.setOrgId(employee.getDeptId());
                user.setOrgName(employee.getDeptName());
                user.setUnitId(employee.getUnitId());
                if (!StringUtils.hasText(user.getRealName())) {
                    user.setRealName(employee.getEmployeeName());
                }
            }
        }
        if (!StringUtils.hasText(user.getPassword())) {
            user.setPassword(PasswordUtil.generateTemporaryPassword());
        }
        if (user.getIsEnabled() == null) {
            user.setIsEnabled(1);
        }
        if (user.getDeleted() == null) {
            user.setDeleted(0);
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedTime(now);
        user.setUpdatedTime(now);
        this.save(user);
    }

    @Override
    public void update(SysUser user) {
        if (user.getId() == null) {
            throw new BusinessException("User id is required");
        }
        // Check if the new username conflicts with another user
        LambdaQueryWrapper<SysUser> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(SysUser::getUsername, user.getUsername())
                    .ne(SysUser::getId, user.getId());
        if (this.count(checkWrapper) > 0) {
            throw new BusinessException("用户名已存在");
        }
        user.setPassword(null);
        user.setUpdatedTime(LocalDateTime.now());
        this.updateById(user);
    }

    @Override
    public void toggleEnabled(Long id) {
        SysUser user = this.getById(id);
        if (user == null) {
            throw new BusinessException("User does not exist");
        }
        user.setIsEnabled(user.getIsEnabled() != null && user.getIsEnabled() == 1 ? 0 : 1);
        user.setUpdatedTime(LocalDateTime.now());
        this.updateById(user);
    }

    @Override
    public String resetPassword(Long id) {
        SysUser user = this.getById(id);
        if (user == null) {
            throw new BusinessException("User does not exist");
        }
        String temporaryPassword = PasswordUtil.generateTemporaryPassword();
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        user.setUpdatedTime(LocalDateTime.now());
        this.updateById(user);
        return temporaryPassword;
    }

    @Override
    public void delete(Long id) {
        SysUser user = this.getById(id);
        if (user == null) {
            throw new BusinessException("User does not exist");
        }
        this.removeById(id);
    }
}
