package com.ccerphr.assessment.service;

import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public interface ExamResultService {

    PageResult<ResultDetailVO> queryDetailPage(ResultQueryDTO queryDTO);

    List<ResultSummaryVO> querySummary(Long examGroupId);

    void exportDetailExcel(Long examGroupId, Long orgId, HttpServletResponse response) throws IOException;

    void exportSummaryExcel(Long examGroupId, HttpServletResponse response) throws IOException;

    List<HistoryExamVO> queryHistory(Long orgId, String year);

    List<ResultDetailVO> queryDetailByExamGroupAndOrg(Long examGroupId, Long orgId);
}
