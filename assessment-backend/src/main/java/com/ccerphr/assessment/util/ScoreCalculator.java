package com.ccerphr.assessment.util;

import com.ccerphr.assessment.entity.BizIndicatorDefinition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 通用得分计算工具类
 * 适用于自评、他评、复核打分场景的结果计算
 *
 * 指标大类计算规则：
 * 1. 控制指标（扣分项）：自评得分输入 2，则结果为 -2，总分减去 2
 * 2. 特殊贡献指标（加分项）：自评得分 3，则结果为 3，总分加 3
 * 3. 否决项目（一票否决）：自评得分 > 0，则结果为其他所有得分的相反数，总分归零
 * 4. 普通指标：结果 = 得分 × 月权重 / 100
 *
 * 精度：所有结果保留 2 位小数，四舍五入
 */
public final class ScoreCalculator {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 指标大类名称常量 */
    public static final String CATEGORY_CONTROL = "控制指标";
    public static final String CATEGORY_SPECIAL_CONTRIBUTION = "特殊贡献指标";
    public static final String CATEGORY_VETO = "否决项目";

    private ScoreCalculator() {
        // 工具类禁止实例化
    }

    /**
     * 计算单项指标的结果（不含否决项逻辑）
     *
     * @param score         得分（自评得分/他评得分/复核得分）
     * @param categoryName  指标大类名称
     * @param weightMonthly 月度权重（百分比，如 15 表示 15%）；可为 null
     * @return 计算后的结果，保留 2 位小数；score 为 null 时返回 null
     */
    public static BigDecimal calculateResult(BigDecimal score, String categoryName, BigDecimal weightMonthly) {
        if (score == null) {
            return null;
        }

        String cat = categoryName != null ? categoryName.trim() : "";

        if (CATEGORY_CONTROL.equals(cat)) {
            // 控制指标（扣分项）：结果 = -|得分|
            return score.abs().negate().setScale(SCALE, ROUNDING);
        } else if (CATEGORY_SPECIAL_CONTRIBUTION.equals(cat)) {
            // 特殊贡献指标（加分项）：结果 = 得分本身
            return score.setScale(SCALE, ROUNDING);
        } else if (CATEGORY_VETO.equals(cat)) {
            // 否决项在此不处理完整逻辑，仅标记为 0
            // 否决项的完整结果需调用 calculateVetoResult() 或 calculateTotalScore()
            return score.compareTo(BigDecimal.ZERO) > 0
                    ? BigDecimal.ZERO.setScale(SCALE, ROUNDING)
                    : BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        } else {
            // 普通指标：结果 = 得分 × 月权重 / 100
            if (weightMonthly != null) {
                return score.multiply(weightMonthly)
                        .divide(HUNDRED, SCALE, ROUNDING);
            } else {
                return score.setScale(SCALE, ROUNDING);
            }
        }
    }

    /**
     * 便捷重载：根据指标定义对象自动提取大类名称和权重，计算单项结果
     * 适用于自评、他评、复核等所有打分场景，统一调用入口
     *
     * @param score     得分（selfScore / peerScore / adminScore 等）
     * @param indicator 指标定义对象（可为 null）
     * @return 计算后的结果；score 为 null 时返回 null
     */
    public static BigDecimal calculateResult(BigDecimal score, BizIndicatorDefinition indicator) {
        String catName = indicator != null ? indicator.getCategoryName() : "";
        BigDecimal weight = indicator != null ? indicator.getWeightMonthly() : null;
        return calculateResult(score, catName, weight);
    }

    /**
     * 便捷重载：根据指标定义构建 ScoreItem
     */
    public static ScoreItem toScoreItem(BigDecimal score, BizIndicatorDefinition indicator) {
        BigDecimal result = calculateResult(score, indicator);
        return new ScoreItem(
                score,
                indicator != null ? indicator.getCategoryName() : "",
                indicator != null ? indicator.getWeightMonthly() : null,
                result);
    }

    /**
     * 计算否决项的结果
     * 规则：如果否决得分 > 0，则结果 = -(其他所有指标结果之和)，使总分归零
     *
     * @param vetoScore        否决项的得分
     * @param otherResultsSum  其他所有指标（非否决项）的结果之和
     * @return 否决项结果；得分 <= 0 时返回 0
     */
    public static BigDecimal calculateVetoResult(BigDecimal vetoScore, BigDecimal otherResultsSum) {
        if (vetoScore == null || vetoScore.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        // 结果 = -(其他结果之和)，使得：其他结果之和 + 否决结果 = 0
        return otherResultsSum != null
                ? otherResultsSum.negate().setScale(SCALE, ROUNDING)
                : BigDecimal.ZERO.setScale(SCALE, ROUNDING);
    }

    /**
     * 计算总得分（含否决逻辑）
     * 规则：
     * - 若任一否决项得分 > 0，总分 = 0
     * - 否则总分 = 所有指标结果之和，保留 2 位小数
     *
     * @param items 所有指标的结果项列表
     * @return 总得分，保留 2 位小数
     */
    public static BigDecimal calculateTotalScore(List<ScoreItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }

        // 先检查是否存在否决项（得分 > 0）
        boolean hasVeto = items.stream()
                .filter(item -> CATEGORY_VETO.equals(item.getCategoryName()))
                .anyMatch(item -> item.getScore() != null && item.getScore().compareTo(BigDecimal.ZERO) > 0);

        if (hasVeto) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }

        // 否则求和
        BigDecimal total = BigDecimal.ZERO;
        for (ScoreItem item : items) {
            if (item.getResult() != null) {
                total = total.add(item.getResult());
            }
        }
        return total.setScale(SCALE, ROUNDING);
    }

    /**
     * 便捷方法：从得分直接计算总得分（一次性完成所有单项计算 + 否决检查）
     * 适用于批量计算场景
     *
     * @param items 所有指标的得分项列表（需包含 score、categoryName、weightMonthly）
     * @return 总得分，保留 2 位小数
     */
    public static BigDecimal calculateTotalScoreFromScores(List<ScoreItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }

        // 第一步：计算每个非否决项的结果
        BigDecimal otherSum = BigDecimal.ZERO;
        boolean hasVeto = false;

        for (ScoreItem item : items) {
            if (CATEGORY_VETO.equals(item.getCategoryName())) {
                if (item.getScore() != null && item.getScore().compareTo(BigDecimal.ZERO) > 0) {
                    hasVeto = true;
                }
            } else {
                BigDecimal result = calculateResult(item.getScore(), item.getCategoryName(), item.getWeightMonthly());
                if (result != null) {
                    otherSum = otherSum.add(result);
                }
            }
        }

        if (hasVeto) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }

        return otherSum.setScale(SCALE, ROUNDING);
    }

    /**
     * 得分项数据结构，用于批量计算
     */
    public static class ScoreItem {
        /** 得分 */
        private BigDecimal score;
        /** 指标大类名称 */
        private String categoryName;
        /** 月度权重 */
        private BigDecimal weightMonthly;
        /** 计算后的结果（calculateTotalScore 使用） */
        private BigDecimal result;

        public BigDecimal getScore() { return score; }
        public void setScore(BigDecimal score) { this.score = score; }

        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

        public BigDecimal getWeightMonthly() { return weightMonthly; }
        public void setWeightMonthly(BigDecimal weightMonthly) { this.weightMonthly = weightMonthly; }

        public BigDecimal getResult() { return result; }
        public void setResult(BigDecimal result) { this.result = result; }

        public ScoreItem() {}

        public ScoreItem(BigDecimal score, String categoryName, BigDecimal weightMonthly) {
            this.score = score;
            this.categoryName = categoryName;
            this.weightMonthly = weightMonthly;
        }

        public ScoreItem(BigDecimal score, String categoryName, BigDecimal weightMonthly, BigDecimal result) {
            this(score, categoryName, weightMonthly);
            this.result = result;
        }
    }
}
