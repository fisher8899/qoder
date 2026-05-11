package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.dto.SelfEvalQueryDTO;
import com.ccerphr.assessment.dto.SelfEvalSaveDTO;
import com.ccerphr.assessment.service.BizSelfEvaluationService;
import com.ccerphr.assessment.util.DataScopeFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/evaluation/self")
public class BizSelfEvaluationController {

    private final BizSelfEvaluationService selfEvaluationService;

    @Value("${app.upload.path:./uploads}")
    private String uploadPath;

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
    public Result<Void> save(@Valid @RequestBody SelfEvalSaveDTO dto) {
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("当前数据范围下不可修改业务数据");
        }
        selfEvaluationService.saveSelfEval(dto);
        return Result.success();
    }

    @PostMapping("/submit")
    public Result<Void> submit(@RequestParam Long examGroupId, @RequestParam Long orgId, HttpServletRequest request) {
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("当前数据范围下不可修改业务数据");
        }
        String submittedBy = getCurrentUser(request);
        selfEvaluationService.submitSelfEval(examGroupId, orgId, submittedBy);
        return Result.success();
    }

    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String url = selfEvaluationService.uploadAttachment(file.getBytes(), file.getOriginalFilename());
        String name = file.getOriginalFilename();
        return Result.success(Map.of("url", url, "name", name));
    }

    @GetMapping("/download/{fileName}")
    public void download(@PathVariable String fileName, HttpServletResponse response) throws IOException {
        Path filePath = Paths.get(uploadPath, "self-eval", fileName);
        if (!Files.exists(filePath)) {
            response.setStatus(404);
            return;
        }
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
        Files.copy(filePath, response.getOutputStream());
        response.getOutputStream().flush();
    }

    private String getCurrentUser(HttpServletRequest request) {
        String user = request.getHeader("X-Current-User");
        if (user == null || user.isEmpty()) {
            user = "system";
        }
        return user;
    }
}
