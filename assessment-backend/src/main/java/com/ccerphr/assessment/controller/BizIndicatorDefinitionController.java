package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.dto.IndicatorApprovalDTO;
import com.ccerphr.assessment.dto.IndicatorProgressVO;
import com.ccerphr.assessment.dto.IndicatorQueryDTO;
import com.ccerphr.assessment.dto.IndicatorSetDTO;
import com.ccerphr.assessment.dto.IndicatorTreeDTO;
import com.ccerphr.assessment.dto.IndicatorVO;
import com.ccerphr.assessment.entity.BizIndicatorDefinition;
import com.ccerphr.assessment.service.BizIndicatorDefinitionService;
import com.ccerphr.assessment.util.DataScopeFilter;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/indicator")
public class BizIndicatorDefinitionController {

    private final BizIndicatorDefinitionService indicatorService;

    public BizIndicatorDefinitionController(BizIndicatorDefinitionService indicatorService) {
        this.indicatorService = indicatorService;
    }

    @GetMapping("/list")
    public Result<PageResult<IndicatorVO>> list(IndicatorQueryDTO queryDTO) {
        return Result.success(indicatorService.queryPage(queryDTO));
    }

    @GetMapping("/{id}")
    public Result<BizIndicatorDefinition> detail(@PathVariable Long id) {
        return Result.success(indicatorService.getDetail(id));
    }

    @GetMapping("/tree/{examGroupId}/{orgId}")
    public Result<List<IndicatorTreeDTO>> tree(@PathVariable Long examGroupId, @PathVariable Long orgId) {
        return Result.success(indicatorService.getIndicatorTree(examGroupId, orgId));
    }

    @PostMapping
    public Result<Long> create(@RequestBody IndicatorSetDTO dto) {
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("当前数据范围下不可修改业务数据");
        }
        Long id = indicatorService.createIndicator(dto);
        return Result.success(id);
    }

    @PutMapping
    public Result<Void> update(@RequestBody IndicatorSetDTO dto) {
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("当前数据范围下不可修改业务数据");
        }
        indicatorService.updateIndicator(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("当前数据范围下不可修改业务数据");
        }
        indicatorService.deleteIndicator(id);
        return Result.success();
    }

    @PostMapping("/submit")
    public Result<Void> submit(@RequestBody List<Long> indicatorIds) {
        indicatorService.submitForApproval(indicatorIds);
        return Result.success();
    }

    @GetMapping("/approval/list")
    public Result<PageResult<IndicatorVO>> approvalList(IndicatorQueryDTO queryDTO, @RequestParam String roleCode) {
        return Result.success(indicatorService.getApprovalList(queryDTO, roleCode));
    }

    @PostMapping("/approve")
    public Result<Void> approve(@RequestBody IndicatorApprovalDTO dto) {
        indicatorService.approve(dto);
        return Result.success();
    }

    @PostMapping("/reject")
    public Result<Void> reject(@RequestBody IndicatorApprovalDTO dto) {
        indicatorService.reject(dto);
        return Result.success();
    }

    @GetMapping("/progress/list")
    public Result<List<IndicatorProgressVO>> queryProgress(
            @RequestParam(required = false) Long examGroupId,
            @RequestParam(required = false) String orgName,
            @RequestParam(required = false) String approvalStatus) {
        List<IndicatorProgressVO> list = indicatorService.queryProgress(examGroupId, orgName, approvalStatus);
        return Result.success(list);
    }
}
