package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.dto.ExamGroupCreateDTO;
import com.ccerphr.assessment.dto.ExamGroupQueryDTO;
import com.ccerphr.assessment.dto.ExamGroupTaskVO;
import com.ccerphr.assessment.entity.BizExamGroup;
import com.ccerphr.assessment.entity.BizExamGroupMember;
import com.ccerphr.assessment.security.UnitScopeAccess;
import com.ccerphr.assessment.service.BizExamGroupService;
import com.ccerphr.assessment.util.DataScopeFilter;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exam-group")
public class BizExamGroupController {

    private static final String READ_ONLY_MESSAGE = "当前数据范围下不允许修改业务数据";

    private final BizExamGroupService examGroupService;

    public BizExamGroupController(BizExamGroupService examGroupService) {
        this.examGroupService = examGroupService;
    }

    @GetMapping("/list")
    public Result<PageResult<BizExamGroup>> list(ExamGroupQueryDTO queryDTO) {
        return Result.success(examGroupService.queryPage(queryDTO));
    }

    @GetMapping("/{id}")
    public Result<BizExamGroup> detail(@PathVariable Long id) {
        return Result.success(examGroupService.getDetail(id));
    }

    @PostMapping
    public Result<Void> create(@RequestBody ExamGroupCreateDTO dto) {
        UnitScopeAccess.requireAdminOrUnitScope();
        if (DataScopeFilter.isReadOnly()) {
            return Result.error(READ_ONLY_MESSAGE);
        }
        examGroupService.createGroup(dto);
        return Result.success();
    }

    @PutMapping
    public Result<Void> update(@RequestBody ExamGroupCreateDTO dto) {
        UnitScopeAccess.requireAdminOrUnitScope();
        if (DataScopeFilter.isReadOnly()) {
            return Result.error(READ_ONLY_MESSAGE);
        }
        examGroupService.updateGroup(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        UnitScopeAccess.requireAdminOrUnitScope();
        if (DataScopeFilter.isReadOnly()) {
            return Result.error(READ_ONLY_MESSAGE);
        }
        examGroupService.deleteGroup(id);
        return Result.success();
    }

    @GetMapping("/{id}/members")
    public Result<List<BizExamGroupMember>> members(@PathVariable Long id) {
        return Result.success(examGroupService.getMembers(id));
    }

    @PostMapping("/{id}/members")
    public Result<Void> addMembers(@PathVariable Long id, @RequestBody List<Long> orgIds) {
        UnitScopeAccess.requireAdminOrUnitScope();
        examGroupService.addMembers(id, orgIds);
        return Result.success();
    }

    @DeleteMapping("/{groupId}/members/{memberId}")
    public Result<Void> removeMember(@PathVariable Long groupId, @PathVariable Long memberId) {
        UnitScopeAccess.requireAdminOrUnitScope();
        examGroupService.removeMember(groupId, memberId);
        return Result.success();
    }

    @PostMapping("/{id}/start")
    public Result<Void> start(@PathVariable Long id) {
        UnitScopeAccess.requireAdminOrUnitScope();
        examGroupService.startGroup(id);
        return Result.success();
    }

    @PostMapping("/{id}/publish-indicator")
    public Result<Void> publishIndicator(@PathVariable Long id) {
        UnitScopeAccess.requireAdminOrUnitScope();
        examGroupService.publishIndicator(id);
        return Result.success();
    }

    @PostMapping("/{id}/start-exam")
    public Result<Void> startExam(@PathVariable Long id) {
        UnitScopeAccess.requireAdminOrUnitScope();
        examGroupService.startExam(id);
        return Result.success();
    }

    @PostMapping("/{id}/start-peer-eval")
    public Result<Void> startPeerEval(@PathVariable Long id) {
        UnitScopeAccess.requireAdminOrUnitScope();
        examGroupService.startPeerEval(id);
        return Result.success();
    }

    @PostMapping("/{id}/pre-publish")
    public Result<Void> prePublish(@PathVariable Long id) {
        UnitScopeAccess.requireAdminOrUnitScope();
        examGroupService.prePublish(id);
        return Result.success();
    }

    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        UnitScopeAccess.requireAdminOrUnitScope();
        examGroupService.publish(id);
        return Result.success();
    }

    @PostMapping("/{id}/cancel-pre-publish")
    public Result<Void> cancelPrePublish(@PathVariable Long id) {
        UnitScopeAccess.requireAdminOrUnitScope();
        examGroupService.cancelPrePublish(id);
        return Result.success();
    }

    @PostMapping("/{id}/restart")
    public Result<Void> restart(@PathVariable Long id) {
        UnitScopeAccess.requireAdminOrUnitScope();
        examGroupService.restartGroup(id);
        return Result.success();
    }

    @GetMapping("/{id}/progress")
    public Result<List<Map<String, Object>>> progress(@PathVariable Long id) {
        return Result.success(examGroupService.getProgress(id));
    }

    @GetMapping("/my-tasks")
    public Result<List<ExamGroupTaskVO>> myTasks(@RequestParam(required = false) String examCategory) {
        return Result.success(examGroupService.getMyTasks(examCategory));
    }
}
