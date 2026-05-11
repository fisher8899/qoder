package com.ccerphr.assessment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.IndicatorApprovalDTO;
import com.ccerphr.assessment.dto.IndicatorProgressVO;
import com.ccerphr.assessment.dto.IndicatorQueryDTO;
import com.ccerphr.assessment.dto.IndicatorSetDTO;
import com.ccerphr.assessment.dto.IndicatorTreeDTO;
import com.ccerphr.assessment.dto.IndicatorVO;
import com.ccerphr.assessment.entity.BizIndicatorDefinition;

import java.util.List;

public interface BizIndicatorDefinitionService extends IService<BizIndicatorDefinition> {

    PageResult<IndicatorVO> queryPage(IndicatorQueryDTO queryDTO);

    BizIndicatorDefinition getDetail(Long id);

    List<IndicatorTreeDTO> getIndicatorTree(Long examGroupId, Long orgId);

    Long createIndicator(IndicatorSetDTO dto);

    void updateIndicator(IndicatorSetDTO dto);

    void deleteIndicator(Long id);

    void submitForApproval(List<Long> indicatorIds);

    PageResult<IndicatorVO> getApprovalList(IndicatorQueryDTO queryDTO, String roleCode);

    void approve(IndicatorApprovalDTO dto);

    void reject(IndicatorApprovalDTO dto);

    List<IndicatorProgressVO> queryProgress(Long examGroupId, String orgName, String approvalStatus);
}
