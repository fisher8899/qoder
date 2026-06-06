package com.ccerphr.assessment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.dto.LeaderQueryDTO;
import com.ccerphr.assessment.entity.SysEmployee;
import com.ccerphr.assessment.entity.SysLeader;
import com.ccerphr.assessment.mapper.SysEmployeeMapper;
import com.ccerphr.assessment.security.RequireRole;
import com.ccerphr.assessment.security.UnitScopeAccess;
import com.ccerphr.assessment.service.SysLeaderService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/leader")
public class SysLeaderController {

    private final SysLeaderService sysLeaderService;
    private final SysEmployeeMapper sysEmployeeMapper;

    public SysLeaderController(SysLeaderService sysLeaderService, SysEmployeeMapper sysEmployeeMapper) {
        this.sysLeaderService = sysLeaderService;
        this.sysEmployeeMapper = sysEmployeeMapper;
    }

    @GetMapping("/list")
    public Result<PageResult<SysLeader>> list(LeaderQueryDTO query) {
        return Result.success(sysLeaderService.queryPage(query));
    }

    @GetMapping("/all")
    public Result<List<SysLeader>> all(@RequestParam(required = false) Long unitId) {
        LambdaQueryWrapper<SysLeader> wrapper = new LambdaQueryWrapper<>();
        if (unitId != null) {
            wrapper.eq(SysLeader::getUnitId, unitId);
        }
        wrapper.orderByAsc(SysLeader::getLeaderName);
        return Result.success(sysLeaderService.list(wrapper));
    }

    @GetMapping("/{id}")
    public Result<SysLeader> detail(@PathVariable Long id) {
        return Result.success(sysLeaderService.getDetail(id));
    }

    @GetMapping("/employees")
    public Result<List<Map<String, Object>>> getEmployeeList(@RequestParam(required = false) Long unitId) {
        LambdaQueryWrapper<SysEmployee> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysEmployee::getIsActive, 1);
        wrapper.eq(SysEmployee::getIsInvalid, 0);
        if (unitId != null) {
            wrapper.eq(SysEmployee::getUnitId, unitId);
        }
        wrapper.orderByAsc(SysEmployee::getEmployeeNo);
        List<SysEmployee> employees = sysEmployeeMapper.selectList(wrapper);
        List<Map<String, Object>> result = employees.stream().map(emp -> {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("id", emp.getId());
            map.put("employeeNo", emp.getEmployeeNo());
            map.put("employeeName", emp.getEmployeeName());
            map.put("deptName", emp.getDeptName());
            map.put("position", emp.getPosition());
            map.put("level", emp.getLevel());
            return map;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    @PostMapping
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<Void> add(@RequestBody @Validated SysLeader leader) {
        UnitScopeAccess.requireAdminOrUnitScope();
        sysLeaderService.addLeader(leader);
        return Result.success();
    }

    @PutMapping
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<Void> update(@RequestBody @Validated SysLeader leader) {
        UnitScopeAccess.requireAdminOrUnitScope();
        sysLeaderService.updateLeader(leader);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @RequireRole({"ADMIN", "FIN_ADMIN"})
    public Result<Void> delete(@PathVariable Long id) {
        UnitScopeAccess.requireAdminOrUnitScope();
        sysLeaderService.deleteLeader(id);
        return Result.success();
    }
}
