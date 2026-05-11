package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.dto.ExamProgressVO;
import com.ccerphr.assessment.dto.ProgressQueryDTO;
import com.ccerphr.assessment.dto.UnfilledItemVO;
import com.ccerphr.assessment.service.ExamProgressService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/progress")
public class ExamProgressController {

    private final ExamProgressService examProgressService;

    public ExamProgressController(ExamProgressService examProgressService) {
        this.examProgressService = examProgressService;
    }

    @GetMapping("/query")
    public Result<List<ExamProgressVO>> query(ProgressQueryDTO queryDTO) {
        return Result.success(examProgressService.queryProgress(queryDTO));
    }

    @GetMapping("/unfilled")
    public Result<List<UnfilledItemVO>> unfilled(@RequestParam Long examGroupId, @RequestParam Long orgId) {
        return Result.success(examProgressService.queryUnfilledItems(examGroupId, orgId));
    }
}
