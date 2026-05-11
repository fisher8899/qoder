package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.dto.*;
import com.ccerphr.assessment.service.ExamResultService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/result")
public class ExamResultController {

    private final ExamResultService examResultService;

    public ExamResultController(ExamResultService examResultService) {
        this.examResultService = examResultService;
    }

    @GetMapping("/query")
    public Result<PageResult<ResultDetailVO>> query(ResultQueryDTO queryDTO) {
        return Result.success(examResultService.queryDetailPage(queryDTO));
    }

    @GetMapping("/summary")
    public Result<List<ResultSummaryVO>> summary(@RequestParam Long examGroupId) {
        return Result.success(examResultService.querySummary(examGroupId));
    }

    @GetMapping("/export/detail")
    public void exportDetail(@RequestParam Long examGroupId,
                             @RequestParam(required = false) Long orgId,
                             HttpServletResponse response) throws IOException {
        examResultService.exportDetailExcel(examGroupId, orgId, response);
    }

    @GetMapping("/export/summary")
    public void exportSummary(@RequestParam Long examGroupId,
                              HttpServletResponse response) throws IOException {
        examResultService.exportSummaryExcel(examGroupId, response);
    }

    @GetMapping("/history")
    public Result<List<HistoryExamVO>> history(@RequestParam Long orgId,
                                                @RequestParam(required = false) String year) {
        return Result.success(examResultService.queryHistory(orgId, year));
    }

    @GetMapping("/detail-by-org")
    public Result<List<ResultDetailVO>> detailByOrg(@RequestParam Long examGroupId, @RequestParam Long orgId) {
        return Result.success(examResultService.queryDetailByExamGroupAndOrg(examGroupId, orgId));
    }
}
