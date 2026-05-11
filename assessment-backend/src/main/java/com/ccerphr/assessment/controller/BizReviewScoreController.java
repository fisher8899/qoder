package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.dto.ReviewQueryDTO;
import com.ccerphr.assessment.dto.ReviewScoreBatchDTO;
import com.ccerphr.assessment.dto.ReviewScoreSaveDTO;
import com.ccerphr.assessment.service.BizReviewScoreService;
import com.ccerphr.assessment.util.DataScopeFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/review")
public class BizReviewScoreController {

    private final BizReviewScoreService reviewScoreService;

    public BizReviewScoreController(BizReviewScoreService reviewScoreService) {
        this.reviewScoreService = reviewScoreService;
    }

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(ReviewQueryDTO queryDTO) {
        return Result.success(reviewScoreService.getReviewList(queryDTO));
    }

    @GetMapping("/{examGroupId}/summary")
    public Result<List<Map<String, Object>>> summary(@PathVariable Long examGroupId) {
        return Result.success(reviewScoreService.getReviewSummary(examGroupId));
    }

    @PostMapping("/save")
    public Result<Void> save(@RequestBody ReviewScoreSaveDTO dto) {
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("当前数据范围下不可修改业务数据");
        }
        reviewScoreService.saveReviewScore(dto);
        return Result.success();
    }

    @PostMapping("/batch-save")
    public Result<Void> batchSave(@RequestBody ReviewScoreBatchDTO dto) {
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("当前数据范围下不可修改业务数据");
        }
        reviewScoreService.batchSaveReviewScore(dto);
        return Result.success();
    }

    @PostMapping("/submit")
    public Result<Void> submit(@RequestParam Long examGroupId, HttpServletRequest request) {
        if (DataScopeFilter.isReadOnly()) {
            return Result.error("当前数据范围下不可修改业务数据");
        }
        String reviewer = getCurrentUser(request);
        reviewScoreService.submitReview(examGroupId, reviewer);
        return Result.success();
    }

    private String getCurrentUser(HttpServletRequest request) {
        String user = request.getHeader("X-Current-User");
        if (user == null || user.isEmpty()) {
            user = "system";
        }
        return user;
    }
}
