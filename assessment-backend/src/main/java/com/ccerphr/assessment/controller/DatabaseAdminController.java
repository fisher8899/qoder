package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.dto.DatabaseQueryDTO;
import com.ccerphr.assessment.dto.DatabaseRowUpdateDTO;
import com.ccerphr.assessment.dto.DatabaseTableDTO;
import com.ccerphr.assessment.dto.DatabaseTablePageDTO;
import com.ccerphr.assessment.security.RequireRole;
import com.ccerphr.assessment.service.DatabaseAdminService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/db-admin")
@ConditionalOnProperty(prefix = "app.db-admin", name = "enabled", havingValue = "true", matchIfMissing = false)
public class DatabaseAdminController {

    private final DatabaseAdminService databaseAdminService;

    public DatabaseAdminController(DatabaseAdminService databaseAdminService) {
        this.databaseAdminService = databaseAdminService;
    }

    @GetMapping("/tables")
    @RequireRole("ADMIN")
    public Result<List<DatabaseTableDTO>> listTables() {
        return Result.success(databaseAdminService.listTables());
    }

    @GetMapping("/rows")
    @RequireRole("ADMIN")
    public Result<DatabaseTablePageDTO> queryRows(DatabaseQueryDTO queryDTO) {
        return Result.success(databaseAdminService.queryTable(queryDTO));
    }

    @PutMapping("/row")
    @RequireRole("ADMIN")
    public Result<Void> updateRow(@RequestBody DatabaseRowUpdateDTO updateDTO) {
        databaseAdminService.updateRow(updateDTO);
        return Result.success();
    }

    @DeleteMapping("/row")
    @RequireRole("ADMIN")
    public Result<Void> deleteRow(@RequestParam String tableName, @RequestParam Long id) {
        databaseAdminService.deleteRow(tableName, id);
        return Result.success();
    }
}
