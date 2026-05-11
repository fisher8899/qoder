package com.ccerphr.assessment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ccerphr.assessment.dto.SelfEvalQueryDTO;
import com.ccerphr.assessment.dto.SelfEvalSaveDTO;
import com.ccerphr.assessment.entity.BizSelfEvaluation;

import java.util.List;
import java.util.Map;

public interface BizSelfEvaluationService extends IService<BizSelfEvaluation> {

    List<Map<String, Object>> getTaskList(SelfEvalQueryDTO queryDTO);

    List<Map<String, Object>> getIndicators(Long examGroupId, Long orgId);

    void saveSelfEval(SelfEvalSaveDTO dto);

    void submitSelfEval(Long examGroupId, Long orgId, String submittedBy);

    String uploadAttachment(byte[] fileData, String originalFilename);
}
