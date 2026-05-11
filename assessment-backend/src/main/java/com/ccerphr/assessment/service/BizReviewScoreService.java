package com.ccerphr.assessment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ccerphr.assessment.dto.ReviewQueryDTO;
import com.ccerphr.assessment.dto.ReviewScoreBatchDTO;
import com.ccerphr.assessment.dto.ReviewScoreSaveDTO;
import com.ccerphr.assessment.entity.BizReviewScore;

import java.util.List;
import java.util.Map;

public interface BizReviewScoreService extends IService<BizReviewScore> {

    List<Map<String, Object>> getReviewList(ReviewQueryDTO queryDTO);

    List<Map<String, Object>> getReviewSummary(Long examGroupId);

    void saveReviewScore(ReviewScoreSaveDTO dto);

    void batchSaveReviewScore(ReviewScoreBatchDTO dto);

    void submitReview(Long examGroupId, String reviewer);
}
