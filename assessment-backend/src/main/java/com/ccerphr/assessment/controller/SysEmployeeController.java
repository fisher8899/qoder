package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.dto.EmployeeQueryDTO;
import com.ccerphr.assessment.entity.SysEmployee;
import com.ccerphr.assessment.service.SysEmployeeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employee")
public class SysEmployeeController {

    private final SysEmployeeService sysEmployeeService;

    public SysEmployeeController(SysEmployeeService sysEmployeeService) {
        this.sysEmployeeService = sysEmployeeService;
    }

    @GetMapping("/list")
    public Result<PageResult<SysEmployee>> list(EmployeeQueryDTO query) {
        return Result.success(sysEmployeeService.listByPage(query));
    }

    @GetMapping("/all")
    public Result<List<SysEmployee>> all() {
        return Result.success(sysEmployeeService.listAll());
    }

    @GetMapping("/{id}")
    public Result<SysEmployee> detail(@PathVariable Long id) {
        return Result.success(sysEmployeeService.getDetail(id));
    }

    @PostMapping
    public Result<Void> add(@RequestBody @Validated SysEmployee employee) {
        sysEmployeeService.add(employee);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody @Validated SysEmployee employee) {
        sysEmployeeService.update(employee);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysEmployeeService.delete(id);
        return Result.success();
    }
}
