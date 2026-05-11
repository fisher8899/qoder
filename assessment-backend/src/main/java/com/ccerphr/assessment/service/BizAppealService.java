package com.ccerphr.assessment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ccerphr.assessment.dto.AppealCreateDTO;
import com.ccerphr.assessment.dto.AppealHandleDTO;
import com.ccerphr.assessment.dto.AppealQueryDTO;
import com.ccerphr.assessment.entity.BizAppeal;

import java.util.List;
import java.util.Map;

public interface BizAppealService extends IService<BizAppeal> {

    List<BizAppeal> getAppealList(AppealQueryDTO queryDTO);

    Long countAppealList(AppealQueryDTO queryDTO);

    Map<String, Object> getAppealDetail(Long id);

    BizAppeal createAppeal(AppealCreateDTO dto, String createdBy);

    void submitAppeal(Long id);

    void reassignAppeal(Long id);

    void handleAppeal(AppealHandleDTO dto, String handledBy);

    List<BizAppeal> getPendingReevalList(Long scorerOrgId);

    void reScoreAppeal(Long id, AppealHandleDTO dto, String handledBy);
}
