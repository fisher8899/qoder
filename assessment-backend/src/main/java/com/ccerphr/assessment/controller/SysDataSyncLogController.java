package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.entity.SysDataSyncLog;
import com.ccerphr.assessment.security.RequireRole;
import com.ccerphr.assessment.service.SysDataSyncLogService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/data-sync")
public class SysDataSyncLogController {

    private final SysDataSyncLogService sysDataSyncLogService;

    public SysDataSyncLogController(SysDataSyncLogService sysDataSyncLogService) {
        this.sysDataSyncLogService = sysDataSyncLogService;
    }

    @GetMapping("/history")
    public Result<PageResult<SysDataSyncLog>> history(@RequestParam(defaultValue = "1") long current,
                                                      @RequestParam(defaultValue = "10") long size) {
        return Result.success(sysDataSyncLogService.queryHistory(current, size));
    }

    @PostMapping("/manual")
    @RequireRole("ADMIN")
    public Result<SysDataSyncLog> manualSync() {
        return Result.success(sysDataSyncLogService.manualSync());
    }

    @GetMapping("/{id}")
    public Result<SysDataSyncLog> detail(@PathVariable Long id) {
        return Result.success(sysDataSyncLogService.getDetail(id));
    }
}
