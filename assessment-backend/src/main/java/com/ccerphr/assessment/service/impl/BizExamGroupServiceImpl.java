package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.ExamGroupCreateDTO;
import com.ccerphr.assessment.dto.ExamGroupQueryDTO;
import com.ccerphr.assessment.dto.ExamGroupTaskVO;
import com.ccerphr.assessment.entity.BizExamGroup;
import com.ccerphr.assessment.entity.BizExamGroupMember;
import com.ccerphr.assessment.entity.SysOrganization;
import com.ccerphr.assessment.enums.ExamStatus;
import com.ccerphr.assessment.mapper.BizExamGroupMapper;
import com.ccerphr.assessment.mapper.BizExamGroupMemberMapper;
import com.ccerphr.assessment.mapper.BizIndicatorDefinitionMapper;
import com.ccerphr.assessment.mapper.SysOrganizationMapper;
import com.ccerphr.assessment.mapper.SysUserMapper;
import com.ccerphr.assessment.entity.SysNotification;
import com.ccerphr.assessment.entity.SysUser;
import com.ccerphr.assessment.service.BizExamGroupService;
import com.ccerphr.assessment.service.SysNotificationService;
import com.ccerphr.assessment.context.DataScopeContext;
import com.ccerphr.assessment.util.DataScopeFilter;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BizExamGroupServiceImpl extends ServiceImpl<BizExamGroupMapper, BizExamGroup> implements BizExamGroupService {

    private final BizExamGroupMemberMapper memberMapper;
    private final SysOrganizationMapper organizationMapper;
    private final BizIndicatorDefinitionMapper indicatorDefinitionMapper;
    private final SysNotificationService notificationService;
    private final SysUserMapper userMapper;

    public BizExamGroupServiceImpl(BizExamGroupMemberMapper memberMapper, SysOrganizationMapper organizationMapper, BizIndicatorDefinitionMapper indicatorDefinitionMapper, SysNotificationService notificationService, SysUserMapper userMapper) {
        this.memberMapper = memberMapper;
        this.organizationMapper = organizationMapper;
        this.indicatorDefinitionMapper = indicatorDefinitionMapper;
        this.notificationService = notificationService;
        this.userMapper = userMapper;
    }

    @Override
    public PageResult<BizExamGroup> queryPage(ExamGroupQueryDTO queryDTO) {
        LambdaQueryWrapper<BizExamGroup> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(queryDTO.getGroupName())) {
            wrapper.like(BizExamGroup::getGroupName, queryDTO.getGroupName());
        }
        if (StringUtils.hasText(queryDTO.getExamType())) {
            wrapper.eq(BizExamGroup::getExamType, queryDTO.getExamType());
        }
        if (StringUtils.hasText(queryDTO.getExamCategory())) {
            wrapper.eq(BizExamGroup::getExamCategory, queryDTO.getExamCategory());
        }
        if (StringUtils.hasText(queryDTO.getStatus())) {
            wrapper.eq(BizExamGroup::getStatus, queryDTO.getStatus());
        }
        // 数据范围过滤
        DataScopeFilter.applyUnitFilter(wrapper, BizExamGroup::getUnitId);
        wrapper.orderByDesc(BizExamGroup::getCreatedTime);
        Page<BizExamGroup> page = page(new Page<>(queryDTO.getCurrent(), queryDTO.getSize()), wrapper);
        PageResult<BizExamGroup> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(page.getRecords());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        return result;
    }

    @Override
    public BizExamGroup getDetail(Long id) {
        BizExamGroup group = getById(id);
        if (group == null) {
            throw new BusinessException("考核组不存在");
        }
        return group;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createGroup(ExamGroupCreateDTO dto) {
        BizExamGroup group = new BizExamGroup();
        BeanUtils.copyProperties(dto, group);
        group.setStatus(ExamStatus.NOT_STARTED.getCode());
        group.setCurrentStep("成员维护");
        group.setProgress(0);
        group.setCreatedTime(LocalDateTime.now());
        group.setUpdatedTime(LocalDateTime.now());
        // 自动填入 unitId
        Long unitId = DataScopeFilter.getAutoFillUnitId();
        if (unitId != null && group.getUnitId() == null) {
            group.setUnitId(unitId);
        }
        save(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateGroup(ExamGroupCreateDTO dto) {
        BizExamGroup group = getById(dto.getId());
        if (group == null) {
            throw new BusinessException("考核组不存在");
        }
        BeanUtils.copyProperties(dto, group);
        group.setUpdatedTime(LocalDateTime.now());
        updateById(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Long id) {
        BizExamGroup group = getById(id);
        if (group == null) {
            throw new BusinessException("考核组不存在");
        }
        // 检查该考核组是否存在成员记录
        LambdaQueryWrapper<BizExamGroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(BizExamGroupMember::getExamGroupId, id);
        long memberCount = memberMapper.selectCount(memberWrapper);
        if (memberCount > 0) {
            throw new BusinessException("该考核组存在成员记录，不可删除");
        }
        removeById(id);
    }

    @Override
    public List<BizExamGroupMember> getMembers(Long examGroupId) {
        LambdaQueryWrapper<BizExamGroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizExamGroupMember::getExamGroupId, examGroupId);
        wrapper.orderByAsc(BizExamGroupMember::getCreatedTime);
        return memberMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addMembers(Long examGroupId, List<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return;
        }
        List<Long> existingOrgIds = memberMapper.selectOrgIdsByExamGroupId(examGroupId);
        for (Long orgId : orgIds) {
            if (existingOrgIds.contains(orgId)) {
                continue;
            }
            SysOrganization org = organizationMapper.selectById(orgId);
            if (org == null) {
                continue;
            }
            BizExamGroupMember member = new BizExamGroupMember();
            member.setExamGroupId(examGroupId);
            member.setOrgId(orgId);
            member.setOrgName(org.getOrgName());
            member.setCreatedTime(LocalDateTime.now());
            memberMapper.insert(member);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long groupId, Long memberId) {
        memberMapper.deleteById(memberId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startGroup(Long id) {
        BizExamGroup group = getById(id);
        if (group == null) {
            throw new BusinessException("考核组不存在");
        }
        group.setStatus(ExamStatus.IN_PROGRESS.getCode());
        group.setCurrentStep("已启动");
        group.setUpdatedTime(LocalDateTime.now());
        updateById(group);

        // 启动后推送通知给成员部门绩效管理员
        sendStartNotifications(group);
    }

    private void sendStartNotifications(BizExamGroup group) {
        // 1. 查询已发送过通知的 recipient_user_id（增量逻辑）
        LambdaQueryWrapper<SysNotification> notiWrapper = new LambdaQueryWrapper<>();
        notiWrapper.eq(SysNotification::getRelatedId, group.getId())
                   .eq(SysNotification::getNotifType, "EXAM_START");
        List<SysNotification> existingNotifications = notificationService.list(notiWrapper);
        Set<Long> alreadyNotifiedUserIds = existingNotifications.stream()
            .map(SysNotification::getRecipientUserId)
            .collect(Collectors.toSet());

        // 2. 查询该考核组的所有成员
        LambdaQueryWrapper<BizExamGroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(BizExamGroupMember::getExamGroupId, group.getId());
        List<BizExamGroupMember> members = memberMapper.selectList(memberWrapper);

        if (members == null || members.isEmpty()) {
            return;
        }

        // 3. 获取成员对应组织的部门绩效管理员
        List<Long> orgIds = members.stream()
            .map(BizExamGroupMember::getOrgId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (orgIds.isEmpty()) {
            return;
        }

        // 查询这些组织的信息（获取 dept_admin_id）
        LambdaQueryWrapper<SysOrganization> orgWrapper = new LambdaQueryWrapper<>();
        orgWrapper.in(SysOrganization::getId, orgIds);
        List<SysOrganization> orgs = organizationMapper.selectList(orgWrapper);

        // 4. 根据考核类别区分链接
        String linkUrl;
        if ("INDICATOR_SET".equals(group.getExamCategory())) {
            linkUrl = "/dept/indicator-set";
        } else if ("PERFORMANCE".equals(group.getExamCategory())) {
            linkUrl = "/dept/self-eval";
        } else {
            linkUrl = "/dept/indicator-set";
        }
        String linkText = "去填写";
        String title = group.getGroupName() + "已启动，请在规定时间前完成填写，并提交";

        // 5. 收集所有 deptAdminId（employee_id），批量查询对应的 user_id
        List<Long> employeeIds = orgs.stream()
            .map(SysOrganization::getDeptAdminId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());

        Map<Long, Long> employeeToUserMap = new HashMap<>();
        if (!employeeIds.isEmpty()) {
            LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.in(SysUser::getEmployeeId, employeeIds)
                       .eq(SysUser::getDeleted, 0);
            List<SysUser> users = userMapper.selectList(userWrapper);
            employeeToUserMap = users.stream()
                .collect(Collectors.toMap(SysUser::getEmployeeId, SysUser::getId, (a, b) -> a));
        }

        // 6. 只给未通知过的用户发送通知（使用映射后的 user_id）
        List<SysNotification> notifications = new ArrayList<>();
        for (SysOrganization org : orgs) {
            if (org.getDeptAdminId() != null) {
                Long userId = employeeToUserMap.get(org.getDeptAdminId());
                if (userId != null && !alreadyNotifiedUserIds.contains(userId)) {
                    SysNotification notification = new SysNotification();
                    notification.setRecipientUserId(userId);
                    notification.setTitle(title);
                    notification.setLinkUrl(linkUrl);
                    notification.setLinkText(linkText);
                    notification.setNotifType("EXAM_START");
                    notification.setRelatedId(group.getId());
                    notification.setIsRead(0);
                    notification.setUnitId(group.getUnitId());
                    notifications.add(notification);
                }
            }
        }

        if (!notifications.isEmpty()) {
            notificationService.saveBatch(notifications);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restartGroup(Long id) {
        BizExamGroup group = getById(id);
        if (group == null) {
            throw new BusinessException("考核组不存在");
        }
        if (!"IN_PROGRESS".equals(group.getStatus())) {
            throw new BusinessException("仅已启动的考核组可以重新启动");
        }
        // 不修改状态，只补发未发的通知
        sendStartNotifications(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishIndicator(Long id) {
        BizExamGroup group = getById(id);
        if (group == null) {
            throw new BusinessException("考核组不存在");
        }
        group.setCurrentStep("指标已发布");
        group.setUpdatedTime(LocalDateTime.now());
        updateById(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startExam(Long id) {
        BizExamGroup group = getById(id);
        if (group == null) {
            throw new BusinessException("考核组不存在");
        }
        group.setCurrentStep("考核中");
        group.setUpdatedTime(LocalDateTime.now());
        updateById(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startPeerEval(Long id) {
        BizExamGroup group = getById(id);
        if (group == null) {
            throw new BusinessException("考核组不存在");
        }
        group.setCurrentStep("他评中");
        group.setUpdatedTime(LocalDateTime.now());
        updateById(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void prePublish(Long id) {
        BizExamGroup group = getById(id);
        if (group == null) {
            throw new BusinessException("考核组不存在");
        }
        group.setStatus(ExamStatus.PRE_PUBLISHED.getCode());
        group.setCurrentStep("预发布");
        group.setUpdatedTime(LocalDateTime.now());
        updateById(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        BizExamGroup group = getById(id);
        if (group == null) {
            throw new BusinessException("考核组不存在");
        }
        group.setStatus(ExamStatus.PUBLISHED.getCode());
        group.setCurrentStep("已发布");
        group.setUpdatedTime(LocalDateTime.now());
        updateById(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelPrePublish(Long id) {
        BizExamGroup group = getById(id);
        if (group == null) {
            throw new BusinessException("考核组不存在");
        }
        group.setStatus(ExamStatus.IN_PROGRESS.getCode());
        group.setCurrentStep("他评中");
        group.setUpdatedTime(LocalDateTime.now());
        updateById(group);
    }

    @Override
    public List<Map<String, Object>> getProgress(Long id) {
        LambdaQueryWrapper<BizExamGroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizExamGroupMember::getExamGroupId, id);
        List<BizExamGroupMember> members = memberMapper.selectList(wrapper);

        List<Map<String, Object>> list = new ArrayList<>();
        for (BizExamGroupMember member : members) {
            Map<String, Object> map = new HashMap<>();
            map.put("orgId", member.getOrgId());
            map.put("orgName", member.getOrgName());
            map.put("memberType", member.getMemberType());
            // 统计该部门指标设定完成情况
            LambdaQueryWrapper<com.ccerphr.assessment.entity.BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
            indWrapper.eq(com.ccerphr.assessment.entity.BizIndicatorDefinition::getExamGroupId, id);
            indWrapper.eq(com.ccerphr.assessment.entity.BizIndicatorDefinition::getOrgId, member.getOrgId());
            long total = getBaseMapper().selectCount(null);
            // 简化处理：指标设定完成进度
            long indicatorCount = countIndicatorByOrg(id, member.getOrgId());
            map.put("indicatorCount", indicatorCount);
            map.put("selfEvalStatus", "待提交");
            map.put("peerEvalStatus", "待打分");
            map.put("reviewStatus", "待复核");
            list.add(map);
        }
        return list;
    }

    private long countIndicatorByOrg(Long examGroupId, Long orgId) {
        LambdaQueryWrapper<com.ccerphr.assessment.entity.BizIndicatorDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.ccerphr.assessment.entity.BizIndicatorDefinition::getExamGroupId, examGroupId);
        wrapper.eq(com.ccerphr.assessment.entity.BizIndicatorDefinition::getOrgId, orgId);
        return indicatorDefinitionMapper.selectCount(wrapper);
    }

    @Override
    public List<ExamGroupTaskVO> getMyTasks(String examCategory) {
        // 1. 获取当前用户的 orgId
        String dataScope = DataScopeContext.getDataScope();
        Long orgId = null;
        if ("ORG".equals(dataScope)) {
            orgId = DataScopeContext.getScopeId();
        }
        if (orgId == null) {
            return new ArrayList<>();
        }

        // 2. 查询该 org 参与的考核组成员记录
        LambdaQueryWrapper<BizExamGroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(BizExamGroupMember::getOrgId, orgId);
        List<BizExamGroupMember> members = memberMapper.selectList(memberWrapper);
        if (members == null || members.isEmpty()) {
            return new ArrayList<>();
        }

        // 3. 获取考核组 ID 列表
        List<Long> examGroupIds = members.stream()
            .map(BizExamGroupMember::getExamGroupId)
            .distinct()
            .collect(Collectors.toList());

        // 4. 查询考核组详情，过滤状态与类别
        LambdaQueryWrapper<BizExamGroup> groupWrapper = new LambdaQueryWrapper<>();
        groupWrapper.in(BizExamGroup::getId, examGroupIds);
        groupWrapper.eq(BizExamGroup::getStatus, ExamStatus.IN_PROGRESS.getCode());
        if (examCategory != null && !examCategory.isEmpty()) {
            groupWrapper.eq(BizExamGroup::getExamCategory, examCategory);
        }
        groupWrapper.orderByDesc(BizExamGroup::getCreatedTime);
        List<BizExamGroup> groups = list(groupWrapper);

        if (groups.isEmpty()) {
            return new ArrayList<>();
        }

        // 5. 查询该 org 在这些考核组中的指标审批状态
        Map<Long, String> groupApprovalMap = new HashMap<>();
        for (BizExamGroup group : groups) {
            LambdaQueryWrapper<com.ccerphr.assessment.entity.BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
            indWrapper.eq(com.ccerphr.assessment.entity.BizIndicatorDefinition::getExamGroupId, group.getId());
            indWrapper.eq(com.ccerphr.assessment.entity.BizIndicatorDefinition::getOrgId, orgId);
            indWrapper.last("LIMIT 1");
            List<com.ccerphr.assessment.entity.BizIndicatorDefinition> indicators = indicatorDefinitionMapper.selectList(indWrapper);
            if (indicators != null && !indicators.isEmpty()) {
                groupApprovalMap.put(group.getId(), indicators.get(0).getApprovalStatus());
            } else {
                groupApprovalMap.put(group.getId(), "DRAFT");
            }
        }

        // 6. 组装返回结果
        // 建立 orgId -> orgName 映射
        Map<Long, String> memberOrgNameMap = members.stream()
            .collect(Collectors.toMap(BizExamGroupMember::getExamGroupId, BizExamGroupMember::getOrgName, (a, b) -> a));

        List<ExamGroupTaskVO> result = new ArrayList<>();
        for (BizExamGroup group : groups) {
            ExamGroupTaskVO vo = new ExamGroupTaskVO();
            vo.setExamGroupId(group.getId());
            vo.setExamGroupName(group.getGroupName());
            vo.setExamType(group.getExamType());
            vo.setExamCategory(group.getExamCategory());
            vo.setStartDate(group.getStartDate());
            vo.setEndDate(group.getEndDate());
            vo.setStatus(group.getStatus());
            vo.setOrgId(orgId);
            vo.setOrgName(memberOrgNameMap.getOrDefault(group.getId(), ""));
            vo.setApprovalStatus(groupApprovalMap.getOrDefault(group.getId(), "DRAFT"));
            result.add(vo);
        }
        return result;
    }
}
