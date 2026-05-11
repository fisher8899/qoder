package com.ccerphr.assessment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.ExamGroupCreateDTO;
import com.ccerphr.assessment.dto.ExamGroupQueryDTO;
import com.ccerphr.assessment.dto.ExamGroupTaskVO;
import com.ccerphr.assessment.entity.BizExamGroup;
import com.ccerphr.assessment.entity.BizExamGroupMember;
import com.ccerphr.assessment.entity.SysOrganization;

import java.util.List;
import java.util.Map;

public interface BizExamGroupService extends IService<BizExamGroup> {

    PageResult<BizExamGroup> queryPage(ExamGroupQueryDTO queryDTO);

    BizExamGroup getDetail(Long id);

    void createGroup(ExamGroupCreateDTO dto);

    void updateGroup(ExamGroupCreateDTO dto);

    void deleteGroup(Long id);

    List<BizExamGroupMember> getMembers(Long examGroupId);

    void addMembers(Long examGroupId, List<Long> orgIds);

    void removeMember(Long groupId, Long memberId);

    void startGroup(Long id);

    void publishIndicator(Long id);

    void startExam(Long id);

    void startPeerEval(Long id);

    void prePublish(Long id);

    void publish(Long id);

    void cancelPrePublish(Long id);

    void restartGroup(Long id);

    List<Map<String, Object>> getProgress(Long id);

    /**
     * 获取当前部门所属的考核组任务列表
     */
    List<ExamGroupTaskVO> getMyTasks(String examCategory);
}
