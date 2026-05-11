package com.ccerphr.assessment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ccerphr.assessment.dto.PeerEvalQueryDTO;
import com.ccerphr.assessment.dto.PeerEvalSaveDTO;
import com.ccerphr.assessment.entity.BizPeerEvaluation;

import java.util.List;
import java.util.Map;

public interface BizPeerEvaluationService extends IService<BizPeerEvaluation> {

    List<Map<String, Object>> getTaskList(PeerEvalQueryDTO queryDTO);

    List<Map<String, Object>> getTargetDepts(Long examGroupId, Long evaluatorOrgId);

    List<Map<String, Object>> getPeerEvalByDept(Long examGroupId, Long evaluatorOrgId, Long targetOrgId);

    List<Map<String, Object>> getPeerEvalByIndicator(Long examGroupId, Long evaluatorOrgId, Long categoryId);

    void savePeerEval(PeerEvalSaveDTO dto);

    void submitPeerEval(Long examGroupId, Long evaluatorOrgId, Long targetOrgId, String submittedBy);

    List<Map<String, Object>> getStatistics(Long examGroupId);
}
