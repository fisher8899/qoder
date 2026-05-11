package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.EmployeeQueryDTO;
import com.ccerphr.assessment.entity.SysEmployee;
import com.ccerphr.assessment.mapper.SysEmployeeMapper;
import com.ccerphr.assessment.service.SysEmployeeService;
import com.ccerphr.assessment.util.DataScopeFilter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SysEmployeeServiceImpl extends ServiceImpl<SysEmployeeMapper, SysEmployee> implements SysEmployeeService {

    @Override
    public PageResult<SysEmployee> listByPage(EmployeeQueryDTO query) {
        LambdaQueryWrapper<SysEmployee> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(SysEmployee::getEmployeeName, query.getKeyword())
                    .or().like(SysEmployee::getEmployeeNo, query.getKeyword()));
        }
        if (query.getDeptId() != null) {
            wrapper.eq(SysEmployee::getDeptId, query.getDeptId());
        }
        if (query.getIsActive() != null) {
            wrapper.eq(SysEmployee::getIsActive, query.getIsActive());
        }
        // 数据范围过滤：单位管理员只能查看本单位人员
        DataScopeFilter.applyUnitFilter(wrapper, SysEmployee::getUnitId);
        wrapper.orderByDesc(SysEmployee::getCreatedTime);
        Page<SysEmployee> page = page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        PageResult<SysEmployee> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(page.getRecords());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        return result;
    }

    @Override
    public List<SysEmployee> listAll() {
        LambdaQueryWrapper<SysEmployee> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysEmployee::getIsActive, 1);
        wrapper.eq(SysEmployee::getIsInvalid, 0);
        // 数据范围过滤
        DataScopeFilter.applyUnitFilter(wrapper, SysEmployee::getUnitId);
        wrapper.orderByAsc(SysEmployee::getEmployeeNo);
        return list(wrapper);
    }

    @Override
    public SysEmployee getDetail(Long id) {
        SysEmployee employee = this.getById(id);
        if (employee == null) {
            throw new BusinessException("人员不存在");
        }
        return employee;
    }

    @Override
    public void add(SysEmployee employee) {
        LocalDateTime now = LocalDateTime.now();
        employee.setCreatedTime(now);
        employee.setUpdatedTime(now);
        this.save(employee);
    }

    @Override
    public void update(SysEmployee employee) {
        if (employee.getId() == null) {
            throw new BusinessException("人员ID不能为空");
        }
        employee.setUpdatedTime(LocalDateTime.now());
        this.updateById(employee);
    }

    @Override
    public void delete(Long id) {
        SysEmployee employee = this.getById(id);
        if (employee == null) {
            throw new BusinessException("人员不存在");
        }
        this.removeById(id);
    }
}
