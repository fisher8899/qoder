package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.*;
import com.ccerphr.assessment.entity.*;
import com.ccerphr.assessment.mapper.*;
import com.ccerphr.assessment.service.ExamResultService;
import com.ccerphr.assessment.util.DataScopeFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExamResultServiceImpl implements ExamResultService {

    private static final long MAX_EXPORT_SIZE = 50000L;

    private final BizIndicatorDefinitionMapper indicatorDefinitionMapper;
    private final BizSelfEvaluationMapper selfEvaluationMapper;
    private final BizPeerEvaluationMapper peerEvaluationMapper;
    private final BizReviewScoreMapper reviewScoreMapper;
    private final BizExamGroupMapper examGroupMapper;
    private final BizExamGroupMemberMapper memberMapper;
    private final SysOrganizationMapper organizationMapper;
    private final SysIndicatorCategoryMapper categoryMapper;
    private final BizMonthlyScoreMapper monthlyScoreMapper;

    public ExamResultServiceImpl(BizIndicatorDefinitionMapper indicatorDefinitionMapper,
                                 BizSelfEvaluationMapper selfEvaluationMapper,
                                 BizPeerEvaluationMapper peerEvaluationMapper,
                                 BizReviewScoreMapper reviewScoreMapper,
                                 BizExamGroupMapper examGroupMapper,
                                 BizExamGroupMemberMapper memberMapper,
                                 SysOrganizationMapper organizationMapper,
                                 SysIndicatorCategoryMapper categoryMapper,
                                 BizMonthlyScoreMapper monthlyScoreMapper) {
        this.indicatorDefinitionMapper = indicatorDefinitionMapper;
        this.selfEvaluationMapper = selfEvaluationMapper;
        this.peerEvaluationMapper = peerEvaluationMapper;
        this.reviewScoreMapper = reviewScoreMapper;
        this.examGroupMapper = examGroupMapper;
        this.memberMapper = memberMapper;
        this.organizationMapper = organizationMapper;
        this.categoryMapper = categoryMapper;
        this.monthlyScoreMapper = monthlyScoreMapper;
    }

    @Override
    public PageResult<ResultDetailVO> queryDetailPage(ResultQueryDTO queryDTO) {
        Long examGroupId = queryDTO.getExamGroupId();
        Long orgId = queryDTO.getOrgId();
        Long categoryId = queryDTO.getCategoryId();

        // 数据库分页查询指标定义
        long current = queryDTO.getCurrent();
        long size = queryDTO.getSize();
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<BizIndicatorDefinition> mpPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size);

        LambdaQueryWrapper<BizIndicatorDefinition> indWrapper = new LambdaQueryWrapper<>();
        indWrapper.eq(BizIndicatorDefinition::getExamGroupId, examGroupId);
        if (orgId != null) {
            indWrapper.eq(BizIndicatorDefinition::getOrgId, orgId);
        }
        if (categoryId != null) {
            indWrapper.eq(BizIndicatorDefinition::getCategoryId, categoryId);
        }
        // 数据范围过滤
        DataScopeFilter.applyFilter(indWrapper, BizIndicatorDefinition::getUnitId, BizIndicatorDefinition::getOrgId);
        indWrapper.orderByAsc(BizIndicatorDefinition::getOrgId).orderByAsc(BizIndicatorDefinition::getSortCode);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<BizIndicatorDefinition> indicatorPage =
                indicatorDefinitionMapper.selectPage(mpPage, indWrapper);
        List<BizIndicatorDefinition> indicators = indicatorPage.getRecords();

        // 批量查询关联数据
        List<Long> indicatorIds = indicators.stream().map(BizIndicatorDefinition::getId).collect(Collectors.toList());
        Map<String, BizSelfEvaluation> selfMap = new HashMap<>();
        Map<String, BizPeerEvaluation> peerMap = new HashMap<>();
        Map<String, BizReviewScore> reviewMap = new HashMap<>();

        if (!indicatorIds.isEmpty()) {
            LambdaQueryWrapper<BizSelfEvaluation> selfWrapper = new LambdaQueryWrapper<>();
            selfWrapper.eq(BizSelfEvaluation::getExamGroupId, examGroupId);
            if (orgId != null) selfWrapper.eq(BizSelfEvaluation::getOrgId, orgId);
            selfWrapper.in(BizSelfEvaluation::getIndicatorId, indicatorIds);
            List<BizSelfEvaluation> selfList = selfEvaluationMapper.selectList(selfWrapper);
            for (BizSelfEvaluation s : selfList) {
                selfMap.put(s.getOrgId() + "_" + s.getIndicatorId(), s);
            }

            LambdaQueryWrapper<BizPeerEvaluation> peerWrapper = new LambdaQueryWrapper<>();
            peerWrapper.eq(BizPeerEvaluation::getExamGroupId, examGroupId);
            if (orgId != null) peerWrapper.eq(BizPeerEvaluation::getTargetOrgId, orgId);
            peerWrapper.in(BizPeerEvaluation::getIndicatorId, indicatorIds);
            List<BizPeerEvaluation> peerList = peerEvaluationMapper.selectList(peerWrapper);
            for (BizPeerEvaluation p : peerList) {
                peerMap.put(p.getTargetOrgId() + "_" + p.getIndicatorId(), p);
            }

            LambdaQueryWrapper<BizReviewScore> reviewWrapper = new LambdaQueryWrapper<>();
            reviewWrapper.eq(BizReviewScore::getExamGroupId, examGroupId);
            if (orgId != null) reviewWrapper.eq(BizReviewScore::getOrgId, orgId);
            reviewWrapper.in(BizReviewScore::getIndicatorId, indicatorIds);
            List<BizReviewScore> reviewList = reviewScoreMapper.selectList(reviewWrapper);
            for (BizReviewScore r : reviewList) {
                reviewMap.put(r.getOrgId() + "_" + r.getIndicatorId(), r);
            }
        }

        List<ResultDetailVO> all = new ArrayList<>();
        for (BizIndicatorDefinition ind : indicators) {
            ResultDetailVO vo = new ResultDetailVO();
            vo.setOrgId(ind.getOrgId());
            vo.setOrgName(ind.getOrgName());
            vo.setCategoryName(ind.getCategoryName());
            vo.setSubCategory(ind.getSubCategory());
            vo.setContent(ind.getContent());
            vo.setTargetDesc(ind.getTargetDesc());
            vo.setWeightAnnual(ind.getWeightAnnual());
            vo.setWeightMonthly(ind.getWeightMonthly());
            vo.setEvaluationStandard(ind.getEvaluationStandard());

            String key = ind.getOrgId() + "_" + ind.getId();
            BizSelfEvaluation self = selfMap.get(key);
            BizPeerEvaluation peer = peerMap.get(key);
            BizReviewScore review = reviewMap.get(key);

            vo.setSelfScore(self != null ? self.getSelfScore() : null);
            vo.setPeerScore(peer != null ? peer.getPeerScore() : null);
            vo.setPeerScoreComment(peer != null ? peer.getScoreComment() : null);
            vo.setAdminScore(review != null ? review.getAdminScore() : null);
            vo.setAdminScoreComment(review != null ? review.getScoreComment() : null);
            vo.setFinalScore(review != null ? review.getFinalScore() : null);

            // 加权得分 = 最终得分 * 月度权重 / 100
            BigDecimal finalScore = vo.getFinalScore();
            BigDecimal weightMonthly = vo.getWeightMonthly();
            if (finalScore != null && weightMonthly != null) {
                vo.setWeightedScore(finalScore.multiply(weightMonthly).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            }
            all.add(vo);
        }

        PageResult<ResultDetailVO> pageResult = new PageResult<>();
        pageResult.setTotal(indicatorPage.getTotal());
        pageResult.setRecords(all);
        pageResult.setCurrent(current);
        pageResult.setSize(size);
        return pageResult;
    }

    @Override
    public List<ResultDetailVO> queryDetailByExamGroupAndOrg(Long examGroupId, Long orgId) {
        ResultQueryDTO dto = new ResultQueryDTO();
        dto.setExamGroupId(examGroupId);
        dto.setOrgId(orgId);
        dto.setCurrent(1L);
        dto.setSize(MAX_EXPORT_SIZE);
        return queryDetailPage(dto).getRecords();
    }

    @Override
    public List<ResultSummaryVO> querySummary(Long examGroupId) {
        // 获取考核组所有成员
        LambdaQueryWrapper<BizExamGroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(BizExamGroupMember::getExamGroupId, examGroupId);
        List<BizExamGroupMember> members = memberMapper.selectList(memberWrapper);

        // 获取所有指标大类
        LambdaQueryWrapper<SysIndicatorCategory> catWrapper = new LambdaQueryWrapper<>();
        catWrapper.eq(SysIndicatorCategory::getIsEnabled, 1);
        List<SysIndicatorCategory> categories = categoryMapper.selectList(catWrapper);
        List<String> categoryNames = categories.stream().map(SysIndicatorCategory::getCategoryName).collect(Collectors.toList());

        List<ResultSummaryVO> result = new ArrayList<>();
        // 批量查询所有成员的 review_score 和 indicator_definition
        List<Long> memberOrgIds = members.stream().map(BizExamGroupMember::getOrgId).distinct().collect(Collectors.toList());
        Map<Long, List<BizReviewScore>> reviewByOrg = new HashMap<>();
        Map<Long, List<BizIndicatorDefinition>> indicatorsByOrg = new HashMap<>();
        if (!memberOrgIds.isEmpty()) {
            List<BizReviewScore> allReviews = reviewScoreMapper.selectList(
                    new LambdaQueryWrapper<BizReviewScore>()
                            .eq(BizReviewScore::getExamGroupId, examGroupId)
                            .in(BizReviewScore::getOrgId, memberOrgIds));
            for (BizReviewScore rs : allReviews) {
                reviewByOrg.computeIfAbsent(rs.getOrgId(), k -> new ArrayList<>()).add(rs);
            }
            List<BizIndicatorDefinition> allIndicators = indicatorDefinitionMapper.selectList(
                    new LambdaQueryWrapper<BizIndicatorDefinition>()
                            .eq(BizIndicatorDefinition::getExamGroupId, examGroupId)
                            .in(BizIndicatorDefinition::getOrgId, memberOrgIds));
            for (BizIndicatorDefinition ind : allIndicators) {
                indicatorsByOrg.computeIfAbsent(ind.getOrgId(), k -> new ArrayList<>()).add(ind);
            }
        }

        for (BizExamGroupMember member : members) {
            ResultSummaryVO vo = new ResultSummaryVO();
            vo.setOrgId(member.getOrgId());
            vo.setOrgName(member.getOrgName());
            Map<String, BigDecimal> catScores = new LinkedHashMap<>();
            for (String cn : categoryNames) {
                catScores.put(cn, BigDecimal.ZERO);
            }

            List<BizReviewScore> reviews = reviewByOrg.getOrDefault(member.getOrgId(), Collections.emptyList());
            List<BizIndicatorDefinition> indicators = indicatorsByOrg.getOrDefault(member.getOrgId(), Collections.emptyList());
            Map<Long, String> indCategoryMap = indicators.stream()
                    .collect(Collectors.toMap(BizIndicatorDefinition::getId, BizIndicatorDefinition::getCategoryName, (a, b) -> a));
            Map<Long, BigDecimal> indWeightMap = indicators.stream()
                    .collect(Collectors.toMap(BizIndicatorDefinition::getId, BizIndicatorDefinition::getWeightMonthly, (a, b) -> a));

            BigDecimal total = BigDecimal.ZERO;
            for (BizReviewScore rs : reviews) {
                String catName = indCategoryMap.get(rs.getIndicatorId());
                BigDecimal weight = indWeightMap.get(rs.getIndicatorId());
                if (catName == null) catName = "其他";
                if (weight == null) weight = BigDecimal.ZERO;

                BigDecimal weighted = BigDecimal.ZERO;
                if (rs.getFinalScore() != null) {
                    weighted = rs.getFinalScore().multiply(weight).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                }
                catScores.merge(catName, weighted, BigDecimal::add);
                total = total.add(weighted);
            }

            vo.setCategoryScores(catScores);
            vo.setTotalScore(total.setScale(2, RoundingMode.HALF_UP));
            result.add(vo);
        }
        return result;
    }

    @Override
    public void exportDetailExcel(Long examGroupId, Long orgId, HttpServletResponse response) throws IOException {
        ResultQueryDTO dto = new ResultQueryDTO();
        dto.setExamGroupId(examGroupId);
        dto.setOrgId(orgId);
        dto.setCurrent(1L);
        dto.setSize(MAX_EXPORT_SIZE);
        List<ResultDetailVO> list = queryDetailPage(dto).getRecords();

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("考核明细表");

            // 表头样式
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String[] headers = {"序号", "部门", "指标大类", "指标小类", "考核内容", "指标/目标", "权重(年度)", "权重(月度)", "考核标准", "自评得分", "他评得分", "管理员打分", "最终得分"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (ResultDetailVO vo : list) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(rowNum - 1);
                row.createCell(1).setCellValue(vo.getOrgName());
                row.createCell(2).setCellValue(vo.getCategoryName());
                row.createCell(3).setCellValue(vo.getSubCategory());
                row.createCell(4).setCellValue(vo.getContent());
                row.createCell(5).setCellValue(vo.getTargetDesc());
                setNumericCell(row, 6, vo.getWeightAnnual());
                setNumericCell(row, 7, vo.getWeightMonthly());
                row.createCell(8).setCellValue(vo.getEvaluationStandard());
                setNumericCell(row, 9, vo.getSelfScore());
                setNumericCell(row, 10, vo.getPeerScore());
                setNumericCell(row, 11, vo.getAdminScore());
                setNumericCell(row, 12, vo.getFinalScore());
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            String filename = URLEncoder.encode("考核明细表.xlsx", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + filename);
            wb.write(response.getOutputStream());
        }
    }

    @Override
    public void exportSummaryExcel(Long examGroupId, HttpServletResponse response) throws IOException {
        List<ResultSummaryVO> list = querySummary(examGroupId);
        if (list.isEmpty()) {
            list = new ArrayList<>();
        }

        // 获取所有指标大类列
        Set<String> categorySet = new LinkedHashSet<>();
        for (ResultSummaryVO vo : list) {
            if (vo.getCategoryScores() != null) {
                categorySet.addAll(vo.getCategoryScores().keySet());
            }
        }
        List<String> categories = new ArrayList<>(categorySet);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("考核汇总表");

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // 总分加粗样式（创建一次，循环中复用）
            CellStyle boldStyle = wb.createCellStyle();
            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            boldStyle.setFont(boldFont);

            // 表头
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("序号");
            headerRow.createCell(1).setCellValue("部门名称");
            int col = 2;
            for (String cat : categories) {
                headerRow.createCell(col++).setCellValue(cat + "得分");
            }
            headerRow.createCell(col).setCellValue("总分");

            for (int i = 0; i <= col; i++) {
                headerRow.getCell(i).setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (ResultSummaryVO vo : list) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(rowNum - 1);
                row.createCell(1).setCellValue(vo.getOrgName());
                int c = 2;
                for (String cat : categories) {
                    BigDecimal score = vo.getCategoryScores() != null ? vo.getCategoryScores().getOrDefault(cat, BigDecimal.ZERO) : BigDecimal.ZERO;
                    setNumericCell(row, c++, score);
                }
                Cell totalCell = row.createCell(c);
                setNumericCell(row, c, vo.getTotalScore());
                totalCell.setCellStyle(boldStyle);
            }

            for (int i = 0; i <= col; i++) {
                sheet.autoSizeColumn(i);
            }

            String filename = URLEncoder.encode("考核汇总表.xlsx", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + filename);
            wb.write(response.getOutputStream());
        }
    }

    @Override
    public List<HistoryExamVO> queryHistory(Long orgId, String year) {
        LambdaQueryWrapper<BizExamGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizExamGroup::getStatus, "PUBLISHED");
        if (StringUtils.hasText(year)) {
            wrapper.apply("YEAR(start_date) = {0}", year);
        }
        // 数据范围过滤
        DataScopeFilter.applyUnitFilter(wrapper, BizExamGroup::getUnitId);
        wrapper.orderByDesc(BizExamGroup::getStartDate);
        List<BizExamGroup> groups = examGroupMapper.selectList(wrapper);

        List<HistoryExamVO> result = new ArrayList<>();

        // 批量查询所有考核组的 review_score 和 indicator_definition
        List<Long> groupIds = groups.stream().map(BizExamGroup::getId).collect(Collectors.toList());
        Map<Long, List<BizReviewScore>> reviewsByGroup = new HashMap<>();
        Map<Long, Map<Long, BigDecimal>> weightMapByGroup = new HashMap<>();
        if (orgId != null && !groupIds.isEmpty()) {
            List<BizReviewScore> allReviews = reviewScoreMapper.selectList(
                    new LambdaQueryWrapper<BizReviewScore>()
                            .in(BizReviewScore::getExamGroupId, groupIds)
                            .eq(BizReviewScore::getOrgId, orgId));
            for (BizReviewScore rs : allReviews) {
                reviewsByGroup.computeIfAbsent(rs.getExamGroupId(), k -> new ArrayList<>()).add(rs);
            }
            List<BizIndicatorDefinition> allIndicators = indicatorDefinitionMapper.selectList(
                    new LambdaQueryWrapper<BizIndicatorDefinition>()
                            .in(BizIndicatorDefinition::getExamGroupId, groupIds)
                            .eq(BizIndicatorDefinition::getOrgId, orgId));
            for (BizIndicatorDefinition ind : allIndicators) {
                weightMapByGroup.computeIfAbsent(ind.getExamGroupId(), k -> new HashMap<>())
                        .put(ind.getId(), ind.getWeightMonthly());
            }
        }

        for (BizExamGroup g : groups) {
            HistoryExamVO vo = new HistoryExamVO();
            vo.setExamGroupId(g.getId());
            vo.setGroupName(g.getGroupName());
            vo.setExamCategory(g.getExamCategory());
            vo.setExamType(g.getExamType());
            vo.setStartDate(g.getStartDate());
            vo.setEndDate(g.getEndDate());
            vo.setStatus(g.getStatus());
            vo.setCurrentStep(g.getCurrentStep());

            // 计算该部门总分
            if (orgId != null) {
                List<BizReviewScore> reviews = reviewsByGroup.getOrDefault(g.getId(), Collections.emptyList());
                Map<Long, BigDecimal> indWeightMap = weightMapByGroup.getOrDefault(g.getId(), Collections.emptyMap());
                BigDecimal total = BigDecimal.ZERO;
                for (BizReviewScore rs : reviews) {
                    BigDecimal weight = indWeightMap.get(rs.getIndicatorId());
                    if (weight == null) weight = BigDecimal.ZERO;
                    if (rs.getFinalScore() != null) {
                        total = total.add(rs.getFinalScore().multiply(weight).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                    }
                }
                vo.setTotalScore(total.setScale(2, RoundingMode.HALF_UP));
            }
            result.add(vo);
        }
        return result;
    }

    private void setNumericCell(Row row, int col, BigDecimal value) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        } else {
            cell.setCellValue("");
        }
    }
}
