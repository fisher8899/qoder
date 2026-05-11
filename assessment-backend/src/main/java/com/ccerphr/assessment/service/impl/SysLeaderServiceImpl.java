package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.LeaderQueryDTO;
import com.ccerphr.assessment.entity.SysEmployee;
import com.ccerphr.assessment.entity.SysLeader;
import com.ccerphr.assessment.mapper.SysEmployeeMapper;
import com.ccerphr.assessment.mapper.SysLeaderMapper;
import com.ccerphr.assessment.service.SysLeaderService;
import com.ccerphr.assessment.util.DataScopeFilter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class SysLeaderServiceImpl extends ServiceImpl<SysLeaderMapper, SysLeader> implements SysLeaderService {

    private final SysEmployeeMapper sysEmployeeMapper;

    public SysLeaderServiceImpl(SysEmployeeMapper sysEmployeeMapper) {
        this.sysEmployeeMapper = sysEmployeeMapper;
    }

    @Override
    public PageResult<SysLeader> queryPage(LeaderQueryDTO query) {
        LambdaQueryWrapper<SysLeader> wrapper = new LambdaQueryWrapper<>();
        if (query.getUnitId() != null) {
            wrapper.eq(SysLeader::getUnitId, query.getUnitId());
        }
        if (StringUtils.hasText(query.getLeaderName())) {
            wrapper.like(SysLeader::getLeaderName, query.getLeaderName());
        }
        // 数据范围过滤 - 按 unit_id 过滤
        DataScopeFilter.applyUnitFilter(wrapper, SysLeader::getUnitId);
        wrapper.orderByDesc(SysLeader::getCreatedTime);
        Page<SysLeader> page = page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        PageResult<SysLeader> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(page.getRecords());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        return result;
    }

    @Override
    public SysLeader getDetail(Long id) {
        SysLeader leader = this.getById(id);
        if (leader == null) {
            throw new BusinessException("分管领导不存在");
        }
        return leader;
    }

    @Override
    public void addLeader(SysLeader leader) {
        // 自动填入 unitId
        Long unitId = DataScopeFilter.getAutoFillUnitId();
        if (unitId != null && leader.getUnitId() == null) {
            leader.setUnitId(unitId);
        }
        fillEmployeeInfo(leader);
        LocalDateTime now = LocalDateTime.now();
        leader.setCreatedTime(now);
        leader.setUpdatedTime(now);
        this.save(leader);
    }

    @Override
    public void updateLeader(SysLeader leader) {
        if (leader.getId() == null) {
            throw new BusinessException("分管领导ID不能为空");
        }
        fillEmployeeInfo(leader);
        leader.setUpdatedTime(LocalDateTime.now());
        this.updateById(leader);
    }

    @Override
    public void deleteLeader(Long id) {
        if (!this.removeById(id)) {
            throw new BusinessException("分管领导不存在或已删除");
        }
    }

    /**
     * 如果传了employeeId，从人员表获取人员信息自动填充leaderName和leaderLevel
     */
    private void fillEmployeeInfo(SysLeader leader) {
        if (leader.getEmployeeId() != null) {
            SysEmployee employee = sysEmployeeMapper.selectById(leader.getEmployeeId());
            if (employee != null) {
                leader.setLeaderName(employee.getEmployeeName());
                if (!StringUtils.hasText(leader.getLeaderLevel()) && StringUtils.hasText(employee.getLevel())) {
                    leader.setLeaderLevel(employee.getLevel());
                }
            }
        }
    }
}
