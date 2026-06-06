package com.ccerphr.assessment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.context.DataScopeContext;
import com.ccerphr.assessment.dto.OrganizationQueryDTO;
import com.ccerphr.assessment.entity.SysEmployee;
import com.ccerphr.assessment.entity.SysOrganization;
import com.ccerphr.assessment.mapper.SysEmployeeMapper;
import com.ccerphr.assessment.security.RequireRole;
import com.ccerphr.assessment.service.SysOrganizationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/organization")
public class SysOrganizationController {

    private final SysOrganizationService sysOrganizationService;
    private final SysEmployeeMapper sysEmployeeMapper;

    public SysOrganizationController(SysOrganizationService sysOrganizationService,
                                     SysEmployeeMapper sysEmployeeMapper) {
        this.sysOrganizationService = sysOrganizationService;
        this.sysEmployeeMapper = sysEmployeeMapper;
    }

    @GetMapping("/list")
    public Result<PageResult<SysOrganization>> list(OrganizationQueryDTO query) {
        return Result.success(sysOrganizationService.queryPage(query));
    }

    @GetMapping("/{id}")
    public Result<SysOrganization> detail(@PathVariable Long id) {
        return Result.success(sysOrganizationService.getDetail(id));
    }

    @GetMapping("/all")
    public Result<List<SysOrganization>> all(@RequestParam(required = false) Long unitId) {
        return Result.success(sysOrganizationService.getAll(unitId));
    }

    @GetMapping("/employees")
    public Result<List<Map<String, Object>>> getEmployeeList(
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) Long deptId) {
        LambdaQueryWrapper<SysEmployee> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysEmployee::getIsActive, 1);
        wrapper.eq(SysEmployee::getIsInvalid, 0);
        if (unitId != null) {
            wrapper.eq(SysEmployee::getUnitId, unitId);
        }
        if (deptId != null) {
            wrapper.eq(SysEmployee::getDeptId, deptId);
        }
        wrapper.orderByAsc(SysEmployee::getEmployeeNo);
        List<SysEmployee> employees = sysEmployeeMapper.selectList(wrapper);
        List<Map<String, Object>> result = employees.stream().map(emp -> {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("id", emp.getId());
            map.put("employeeNo", emp.getEmployeeNo());
            map.put("employeeName", emp.getEmployeeName());
            map.put("deptId", emp.getDeptId());
            map.put("deptName", emp.getDeptName());
            map.put("unitId", emp.getUnitId());
            map.put("position", emp.getPosition());
            map.put("level", emp.getLevel());
            return map;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    @PostMapping
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<Void> add(@RequestBody @Validated SysOrganization organization) {
        validateUnitRoleAccess();
        sysOrganizationService.addOrganization(organization);
        return Result.success();
    }

    @PutMapping
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<Void> update(@RequestBody @Validated SysOrganization organization) {
        validateUnitRoleAccess();
        sysOrganizationService.updateOrganization(organization);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<Void> delete(@PathVariable Long id) {
        validateUnitRoleAccess();
        sysOrganizationService.deleteOrganization(id);
        return Result.success();
    }

    private void validateUnitRoleAccess() {
        String roleCode = DataScopeContext.getRoleCode();
        if ("ADMIN".equals(roleCode)) {
            return;
        }
        if ("UNIT".equals(DataScopeContext.getDataScope())) {
            return;
        }
        if (roleCode == null || roleCode.isBlank()) {
            throw new BusinessException(403, "无权限访问该资源");
        }
        throw new BusinessException(403, "无权限访问该资源，需要管理员或单位范围职责");
    }
}
