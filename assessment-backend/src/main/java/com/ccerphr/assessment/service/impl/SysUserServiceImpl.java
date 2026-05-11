package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.common.SystemConstants;
import com.ccerphr.assessment.dto.UserQueryDTO;
import com.ccerphr.assessment.entity.SysEmployee;
import com.ccerphr.assessment.entity.SysUser;
import com.ccerphr.assessment.mapper.SysEmployeeMapper;
import com.ccerphr.assessment.mapper.SysUserMapper;
import com.ccerphr.assessment.service.SysUserService;
import com.ccerphr.assessment.util.DataScopeFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

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
        // 数据范围过滤：单位管理员只能查看本单位用户
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
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    @Override
    public void add(SysUser user) {
        // 检查用户名是否已存在
        Long count = this.lambdaQuery().eq(SysUser::getUsername, user.getUsername()).count();
        if (count > 0) {
            throw new BusinessException("用户名已存在，请更换用户名");
        }
        // 角色通过权限分配管理，新增用户时如果未指定roleCode则设置默认值
        if (user.getRoleCode() == null || user.getRoleCode().isEmpty()) {
            user.setRoleCode("USER");
            user.setRoleName("普通用户");
        }
        // 如果关联了人员，自动填充orgId和orgName
        if (user.getEmployeeId() != null) {
            SysEmployee employee = employeeMapper.selectById(user.getEmployeeId());
            if (employee != null) {
                user.setOrgId(employee.getDeptId());
                user.setOrgName(employee.getDeptName());
            }
        }
        // 设置默认值，防止NOT NULL约束或逻辑删除字段异常
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
            throw new BusinessException("用户ID不能为空");
        }
        user.setPassword(null);
        user.setUpdatedTime(LocalDateTime.now());
        this.updateById(user);
    }

    @Override
    public void toggleEnabled(Long id) {
        SysUser user = this.getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setIsEnabled(user.getIsEnabled() != null && user.getIsEnabled() == 1 ? 0 : 1);
        user.setUpdatedTime(LocalDateTime.now());
        this.updateById(user);
    }

    @Override
    public void resetPassword(Long id) {
        SysUser user = this.getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 使用随机生成的密码代替硬编码的 "123456"
        String newPassword = SystemConstants.Password.generateRandom();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedTime(LocalDateTime.now());
        this.updateById(user);
        // 将新密码设置到 user 对象中，以便调用方获取明文密码通知用户
        user.setPassword(newPassword);
    }

    /**
     * 重置密码并返回明文密码
     * @param id 用户ID
     * @return 明文密码，用于通知用户
     */
    public String resetPasswordAndGetPlain(Long id) {
        SysUser user = this.getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        String newPassword = SystemConstants.Password.generateRandom();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedTime(LocalDateTime.now());
        this.updateById(user);
        return newPassword;
    }

    @Override
    public void delete(Long id) {
        SysUser user = this.getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        this.removeById(id);
    }
}
