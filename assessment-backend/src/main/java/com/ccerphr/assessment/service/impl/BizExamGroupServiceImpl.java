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
import java.util.HashSet;
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
        BizExamGroup group = requireAccessibleGroup(id);
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
        BizExamGroup group = requireAccessibleGroup(dto.getId());
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
        BizExamGroup group = requireAccessibleGroup(id);
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
        requireAccessibleGroup(examGroupId);
        LambdaQueryWrapper<BizExamGroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizExamGroupMember::getExamGroupId, examGroupId);
        wrapper.orderByAsc(BizExamGroupMember::getCreatedTime);
        return memberMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addMembers(Long examGroupId, List<Long> orgIds) {
        requireAccessibleGroup(examGroupId);
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
            assertOrgAssignable(org);
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
        requireAccessibleGroup(groupId);
        BizExamGroupMember member = memberMapper.selectById(memberId);
        if (member == null || !groupId.equals(member.getExamGroupId())) {
            throw new BusinessException(404, "Exam group member not found");
        }
        memberMapper.deleteById(memberId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startGroup(Long id) {
        BizExamGroup group = requireAccessibleGroup(id);
        if (group == null) {
            throw new BusinessException("考核组不存在");
        }
        // 状态机校验：仅"待启动"状态可启动
        if (!ExamStatus.NOT_STARTED.getCode().equals(group.getStatus())) {
            throw new BusinessException("仅待启动状态的考核组可以启动，当前状态：" + group.getStatus());
        }
        group.setStatus(ExamStatus.IN_PROGRESS.getCode());
        group.setCurrentStep("已启动");
        group.setUpdatedTime(LocalDateTime.now());
        updateById(group);

        // 启动后推送通知给成员部门绩效管理员
        sendStartNotifications(group);
    }

    private void sendStartNotifications(BizExamGroup group) {
        String linkUrl;
        if ("INDICATOR_SET".equals(group.getExamCategory())) {
            linkUrl = "/dept/indicator-set";
        } else if ("PERFORMANCE".equals(group.getExamCategory())) {
            linkUrl = "/dept/self-eval";
        } else {
            linkUrl = "/dept/indicator-set";
        }
        String linkText = "去填写";
        String title = group.getGroupName() + "已经启动，请尽快完成设定和提交。";
        sendNotifications(group.getId(), "EXAM_START", title, linkUrl, linkText, "DEPT_ADMIN");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restartGroup(Long id) {
        BizExamGroup group = requireAccessibleGroup(id);
        if (group == null) {
            throw new BusinessException("考核组不存在");
        }
        // 状态机校验：仅"已发布"状态可重新启动（补发通知）
        if (!ExamStatus.PUBLISHED.getCode().equals(group.getStatus()) &&
            !ExamStatus.IN_PROGRESS.getCode().equals(group.getStatus())) {
            throw new BusinessException("仅已发布或进行中状态的考核组可以重新推送通知，当前状态：" + group.getStatus());
        }
        // 不修改状态，只补发未发的通知
        sendStartNotifications(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishIndicator(Long id) {
        BizExamGroup group = requireAccessibleGroup(id);
        if (group == null) {
            throw new BusinessException("考核组不存在");
        }
        // 状态机校验：仅"进行中"状态可执行
        if (!ExamStatus.IN_PROGRESS.getCode().equals(group.getStatus())) {
            throw new BusinessException("仅进行中状态的考核组可发布指标，当前状态：" + group.getStatus());
        }
        group.setCurrentStep("指标已发布");
        group.setUpdatedTime(LocalDateTime.now());
        updateById(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startExam(Long id) {
        BizExamGroup group = requireAccessibleGroup(id);
        if (group == null) {
            throw new BusinessException("考核组不存在");
        }
        // 状态机校验：仅"进行中"状态可执行
        if (!ExamStatus.IN_PROGRESS.getCode().equals(group.getStatus())) {
            throw new BusinessException("仅进行中状态的考核组可开始考核，当前状态：" + group.getStatus());
        }
        group.setCurrentStep("考核中");
        group.setUpdatedTime(LocalDateTime.now());
        updateById(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startPeerEval(Long id) {
        BizExamGroup group = requireAccessibleGroup(id);
        if (group == null) {
            throw new BusinessException("考核组不存在");
        }
        // 状态机校验：仅"进行中"状态可执行
        if (!ExamStatus.IN_PROGRESS.getCode().equals(group.getStatus())) {
            throw new BusinessException("仅进行中状态的考核组可开始他评，当前状态：" + group.getStatus());
        }
        group.setCurrentStep("他评中");
        group.setUpdatedTime(LocalDateTime.now());
        updateById(group);
        
        // 启动他评后，发送通知给部门绩效管理员
        sendPeerStartNotifications(group);
    }

    /**
     * 发送他评启动通知给部门绩效管理员
     */
    private void sendPeerStartNotifications(BizExamGroup group) {
        String linkUrl = "/dept/peer-eval";
        String linkText = "去进行他评";
        String title = group.getGroupName() + "他评已经启动，请及时尽快完成！";
        sendNotifications(group.getId(), "PEER_START", title, linkUrl, linkText, "DEPT_ADMIN");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void prePublish(Long id) {
        BizExamGroup group = requireAccessibleGroup(id);
        if (group == null) {
            throw new BusinessException("考核组不存在");
        }
        // 状态机校验：仅"进行中"状态可预发布
        if (!ExamStatus.IN_PROGRESS.getCode().equals(group.getStatus())) {
            throw new BusinessException("仅进行中状态的考核组可预发布，当前状态：" + group.getStatus());
        }
        // 可选：校验所有部门是否已完成复核
        group.setStatus(ExamStatus.PRE_PUBLISHED.getCode());
        group.setCurrentStep("预发布");
        group.setUpdatedTime(LocalDateTime.now());
        updateById(group);
        
        // 预发布后发送通知给部门绩效管理员
        sendPrePublishNotifications(group);
    }

    /**
     * 发送绩效预发布通知给部门绩效管理员
     */
    private void sendPrePublishNotifications(BizExamGroup group) {
        String linkUrl = "/dept/performance-result?examGroupId=" + group.getId();
        String linkText = "去查看";
        String title = group.getGroupName() + "绩效结果预发布，请大家尽快查看，如有异议请及时申诉。";
        sendNotifications(group.getId(), "PRE_PUBLISH", title, linkUrl, linkText, "DEPT_ADMIN");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        BizExamGroup group = requireAccessibleGroup(id);
        if (group == null) {
            throw new BusinessException("考核组不存在");
        }
        // 状态机校验：仅"预发布"状态可发布
        if (!ExamStatus.PRE_PUBLISHED.getCode().equals(group.getStatus())) {
            throw new BusinessException("仅预发布状态的考核组可发布，当前状态：" + group.getStatus());
        }
        group.setStatus(ExamStatus.PUBLISHED.getCode());
        group.setCurrentStep("已发布");
        group.setUpdatedTime(LocalDateTime.now());
        updateById(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelPrePublish(Long id) {
        BizExamGroup group = requireAccessibleGroup(id);
        if (group == null) {
            throw new BusinessException("考核组不存在");
        }
        // 状态机校验：仅"预发布"状态可取消
        if (!ExamStatus.PRE_PUBLISHED.getCode().equals(group.getStatus())) {
            throw new BusinessException("仅预发布状态的考核组可取消预发布，当前状态：" + group.getStatus());
        }
        group.setStatus(ExamStatus.IN_PROGRESS.getCode());
        group.setCurrentStep("他评中");
        group.setUpdatedTime(LocalDateTime.now());
        updateById(group);
    }

    /**
     * 通用通知发送方法：查询已发通知 → 查询成员 → 查询组织 → 查询用户 → 批量插入
     */
    private void sendNotifications(Long examGroupId, String notifType, String title,
                                   String linkUrl, String linkText, String roleCode) {
        BizExamGroup group = getById(examGroupId);
        if (group == null) {
            return;
        }

        // 1. 查询已发送过的通知
        LambdaQueryWrapper<SysNotification> notiWrapper = new LambdaQueryWrapper<>();
        notiWrapper.eq(SysNotification::getRelatedId, examGroupId)
                   .eq(SysNotification::getNotifType, notifType);
        List<SysNotification> existingNotifications = notificationService.list(notiWrapper);
        Set<Long> alreadyNotifiedUserIds = existingNotifications.stream()
            .map(SysNotification::getRecipientUserId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Set<Long> alreadyNotifiedOrgIds = existingNotifications.stream()
            .map(SysNotification::getOrgId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        // 2. 查询该考核组的所有成员
        LambdaQueryWrapper<BizExamGroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(BizExamGroupMember::getExamGroupId, examGroupId);
        List<BizExamGroupMember> members = memberMapper.selectList(memberWrapper);

        if (members == null || members.isEmpty()) {
            return;
        }

        // 3. 获取成员对应组织
        List<Long> orgIds = members.stream()
            .map(BizExamGroupMember::getOrgId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (orgIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<SysOrganization> orgWrapper = new LambdaQueryWrapper<>();
        orgWrapper.in(SysOrganization::getId, orgIds);
        List<SysOrganization> orgs = organizationMapper.selectList(orgWrapper);

        // 4. 批量查询 deptAdminId -> userId 映射
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

        // 5. 只给未通知过的部门发送通知
        Set<Long> pendingNotifiedUserIds = new HashSet<>(alreadyNotifiedUserIds);
        Set<Long> pendingNotifiedOrgIds = new HashSet<>(alreadyNotifiedOrgIds);
        List<SysNotification> notifications = new ArrayList<>();
        for (SysOrganization org : orgs) {
            if (pendingNotifiedOrgIds.contains(org.getId())) {
                continue;
            }
            if (org.getDeptAdminId() != null) {
                Long userId = employeeToUserMap.get(org.getDeptAdminId());
                if (userId != null && !pendingNotifiedUserIds.contains(userId)) {
                    SysNotification notification = new SysNotification();
                    notification.setRecipientUserId(userId);
                    notification.setTitle(title);
                    notification.setContent(title);
                    notification.setLinkUrl(linkUrl);
                    notification.setLinkText(linkText);
                    notification.setNotifType(notifType);
                    notification.setRelatedId(examGroupId);
                    notification.setIsRead(0);
                    notification.setUnitId(group.getUnitId());
                    notification.setRoleCode(roleCode);
                    notification.setOrgId(org.getId());
                    notification.setCreatedTime(LocalDateTime.now());
                    notifications.add(notification);
                    pendingNotifiedUserIds.add(userId);
                    pendingNotifiedOrgIds.add(org.getId());
                }
            }
        }

        if (!notifications.isEmpty()) {
            notificationService.saveBatch(notifications);
        }
    }

    @Override
    public List<Map<String, Object>> getProgress(Long id) {
        requireAccessibleGroup(id);
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

    private BizExamGroup requireAccessibleGroup(Long id) {
        BizExamGroup group = getById(id);
        if (group == null) {
            throw new BusinessException(404, "Exam group not found");
        }
        assertGroupAccessible(group);
        return group;
    }

    private void assertGroupAccessible(BizExamGroup group) {
        String dataScope = DataScopeContext.getDataScope();
        if ("ALL".equals(dataScope)) {
            return;
        }
        Long scopeId = DataScopeContext.getScopeId();
        if (scopeId == null || scopeId == 0L) {
            throw new BusinessException(403, "No permission to access this exam group");
        }
        if ("UNIT".equals(dataScope)) {
            if (scopeId.equals(group.getUnitId())) {
                return;
            }
            throw new BusinessException(403, "Exam group is outside current unit scope");
        }
        if ("ORG".equals(dataScope)) {
            LambdaQueryWrapper<BizExamGroupMember> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BizExamGroupMember::getExamGroupId, group.getId());
            wrapper.eq(BizExamGroupMember::getOrgId, scopeId);
            if (memberMapper.selectCount(wrapper) > 0) {
                return;
            }
            throw new BusinessException(403, "Exam group is outside current organization scope");
        }
        throw new BusinessException(403, "No permission to access this exam group");
    }

    private void assertOrgAssignable(SysOrganization org) {
        String dataScope = DataScopeContext.getDataScope();
        if ("ALL".equals(dataScope)) {
            return;
        }
        if ("UNIT".equals(dataScope) && DataScopeContext.getScopeId().equals(org.getUnitId())) {
            return;
        }
        if ("ORG".equals(dataScope) && DataScopeContext.getScopeId().equals(org.getId())) {
            return;
        }
        throw new BusinessException(403, "Organization is outside current data scope");
    }

    @Override
    public List<ExamGroupTaskVO> getMyTasks(String examCategory) {
        // 1. 获取当前用户的 orgId 列表
        String dataScope = DataScopeContext.getDataScope();
        List<Long> orgIdList = new ArrayList<>();
        
        if ("ORG".equals(dataScope)) {
            orgIdList.add(DataScopeContext.getScopeId());
        } else if ("UNIT".equals(dataScope)) {
            // UNIT 范围：查询该 UNIT 下的所有部门
            Long scopeId = DataScopeContext.getScopeId();
            if (scopeId != null) {
                LambdaQueryWrapper<SysOrganization> orgWrapper = new LambdaQueryWrapper<>();
                orgWrapper.eq(SysOrganization::getUnitId, scopeId);
                List<SysOrganization> orgs = organizationMapper.selectList(orgWrapper);
                if (orgs != null && !orgs.isEmpty()) {
                    orgIdList = orgs.stream().map(SysOrganization::getId).collect(Collectors.toList());
                }
            }
        }
        
        if (orgIdList.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 查询这些 org 参与的考核组成员记录
        LambdaQueryWrapper<BizExamGroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.in(BizExamGroupMember::getOrgId, orgIdList);
        List<BizExamGroupMember> members = memberMapper.selectList(memberWrapper);
        if (members == null || members.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 3. 获取考核组 ID 列表
        List<Long> examGroupIds = members.stream()
            .map(BizExamGroupMember::getExamGroupId)
            .distinct()
            .collect(Collectors.toList());

        // 4. 查询考核组详情，过滤类别，不限制状态（返回所有状态的考核组，包括已完成的）
        LambdaQueryWrapper<BizExamGroup> groupWrapper = new LambdaQueryWrapper<>();
        groupWrapper.in(BizExamGroup::getId, examGroupIds);
        if (examCategory != null && !examCategory.isEmpty()) {
            groupWrapper.eq(BizExamGroup::getExamCategory, examCategory);
        }
        // 不限制状态，返回所有状态的考核组
        groupWrapper.orderByDesc(BizExamGroup::getCreatedTime);
        groupWrapper.last("LIMIT 50"); // 最多返回50条
        List<BizExamGroup> groups = list(groupWrapper);

        if (groups.isEmpty()) {
            return new ArrayList<>();
        }

        // 5. 按 examGroupId 分组 members，按(考核组+部门)维度拆分任务
        Map<Long, List<BizExamGroupMember>> membersByGroup =
            members.stream().collect(Collectors.groupingBy(BizExamGroupMember::getExamGroupId));

        // 6. 组装返回结果：每个 (examGroupId + orgId) 生成一条 VO
        List<ExamGroupTaskVO> result = new ArrayList<>();
        for (BizExamGroup group : groups) {
            List<BizExamGroupMember> groupMembers = membersByGroup.getOrDefault(group.getId(), java.util.Collections.emptyList());
            for (BizExamGroupMember m : groupMembers) {
                ExamGroupTaskVO vo = new ExamGroupTaskVO();
                vo.setExamGroupId(group.getId());
                vo.setExamGroupName(group.getGroupName());
                vo.setExamType(group.getExamType());
                vo.setExamCategory(group.getExamCategory());
                vo.setStartDate(group.getStartDate());
                vo.setEndDate(group.getEndDate());
                vo.setStatus(group.getStatus());
                vo.setUnitId(group.getUnitId());
                vo.setOrgId(m.getOrgId());
                vo.setOrgName(m.getOrgName());

                // 查询该 org 在该考核组中的指标审批状态
                LambdaQueryWrapper<com.ccerphr.assessment.entity.BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
                indWrapper.eq(com.ccerphr.assessment.entity.BizIndicatorDefinition::getExamGroupId, group.getId());
                indWrapper.eq(com.ccerphr.assessment.entity.BizIndicatorDefinition::getOrgId, m.getOrgId());
                indWrapper.last("LIMIT 1");
                List<com.ccerphr.assessment.entity.BizIndicatorDefinition> indicators = indicatorDefinitionMapper.selectList(indWrapper);
                if (indicators != null && !indicators.isEmpty()) {
                    vo.setApprovalStatus(indicators.get(0).getApprovalStatus());
                } else {
                    vo.setApprovalStatus("DRAFT");
                }

                result.add(vo);
            }
        }
        return result;
    }
}

