package com.ccerphr.assessment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ccerphr.assessment.dto.SelfEvalQueryDTO;
import com.ccerphr.assessment.dto.SelfEvalSaveDTO;
import com.ccerphr.assessment.entity.BizSelfEvaluation;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface BizSelfEvaluationService extends IService<BizSelfEvaluation> {

    List<Map<String, Object>> getTaskList(SelfEvalQueryDTO queryDTO);

    List<Map<String, Object>> getIndicators(Long examGroupId, Long orgId);

    Long saveSelfEval(SelfEvalSaveDTO dto);

    void submitSelfEval(Long examGroupId, Long orgId, String submittedBy);

    void withdrawSelfEval(Long examGroupId, Long orgId);

    String uploadAttachment(byte[] fileData, String originalFilename);

    Path resolveAttachmentForDownloadById(Long id);

    void deleteAttachment(Long id);
}
