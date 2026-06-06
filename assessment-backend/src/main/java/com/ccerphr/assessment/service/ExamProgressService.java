package com.ccerphr.assessment.service;

import com.ccerphr.assessment.dto.ExamProgressVO;
import com.ccerphr.assessment.dto.ExamProgressDetailVO;
import com.ccerphr.assessment.dto.UnfilledItemVO;
import com.ccerphr.assessment.dto.ProgressQueryDTO;

import java.util.List;

public interface ExamProgressService {

    List<ExamProgressVO> queryProgress(ProgressQueryDTO queryDTO);

    List<ExamProgressDetailVO> queryProgressDetail(Long examGroupId, Long orgId);

    List<UnfilledItemVO> queryUnfilledItems(Long examGroupId, Long orgId);
}
