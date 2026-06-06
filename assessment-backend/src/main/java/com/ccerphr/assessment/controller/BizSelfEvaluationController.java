package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.dto.SelfEvalQueryDTO;
import com.ccerphr.assessment.dto.SelfEvalSaveDTO;
import com.ccerphr.assessment.security.SecurityUtil;
import com.ccerphr.assessment.service.BizSelfEvaluationService;
import com.ccerphr.assessment.util.DataScopeFilter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/evaluation/self")
public class BizSelfEvaluationController {

    private final BizSelfEvaluationService selfEvaluationService;

    public BizSelfEvaluationController(BizSelfEvaluationService selfEvaluationService) {
        this.selfEvaluationService = selfEvaluationService;
    }

    @GetMapping("/task")
    public Result<List<Map<String, Object>>> taskList(SelfEvalQueryDTO queryDTO) {
        return Result.success(selfEvaluationService.getTaskList(queryDTO));
    }

    @GetMapping("/indicators")
    public Result<List<Map<String, Object>>> indicators(@RequestParam Long examGroupId, @RequestParam Long orgId) {
        return Result.success(selfEvaluationService.getIndicators(examGroupId, orgId));
    }

    @PostMapping("/save")
    public Result<Long> save(@Valid @RequestBody SelfEvalSaveDTO dto) {
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("当前数据范围下不可修改业务数据");
        }
        Long savedId = selfEvaluationService.saveSelfEval(dto);
        return Result.success(savedId);
    }

    @PostMapping("/submit")
    public Result<Void> submit(@RequestParam Long examGroupId, @RequestParam Long orgId) {
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("当前数据范围下不可修改业务数据");
        }
        String submittedBy = SecurityUtil.getCurrentUserName();
        selfEvaluationService.submitSelfEval(examGroupId, orgId, submittedBy);
        return Result.success();
    }

    @PostMapping("/withdraw")
    public Result<Void> withdraw(@RequestParam Long examGroupId, @RequestParam Long orgId) {
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("当前数据范围下不可修改业务数据");
        }
        selfEvaluationService.withdrawSelfEval(examGroupId, orgId);
        return Result.success();
    }

    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String storageKey = selfEvaluationService.uploadAttachment(file.getBytes(), file.getOriginalFilename());
        String originalName = file.getOriginalFilename() == null ? storageKey : file.getOriginalFilename();
        return Result.success(Map.of(
                "url", storageKey,
                "name", originalName
        ));
    }

    @GetMapping("/download/{id}")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Path filePath = selfEvaluationService.resolveAttachmentForDownloadById(id);
        String contentType = Files.probeContentType(filePath);
        response.setContentType(contentType != null ? contentType : "application/octet-stream");
        response.setHeader(
                "Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(filePath.getFileName().toString(), StandardCharsets.UTF_8)
        );
        Files.copy(filePath, response.getOutputStream());
        response.getOutputStream().flush();
    }

    @DeleteMapping("/{id}/attachment")
    public Result<Void> deleteAttachment(@PathVariable Long id) {
        selfEvaluationService.deleteAttachment(id);
        return Result.success();
    }
}
