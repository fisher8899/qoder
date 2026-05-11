package com.ccerphr.assessment.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.ccerphr.assessment.context.DataScopeContext;

/**
 * 数据范围过滤工具 - 为 MyBatis-Plus 查询自动添加数据范围条件
 */
public class DataScopeFilter {

    /**
     * 为查询添加 unit_id 过滤条件
     * - ALL: 不加条件
     * - UNIT: wrapper.eq(unitId, scopeId)
     * - ORG: 也通过 unitId 过滤（业务表 unitId 关联）
     *
     * @param wrapper      MyBatis-Plus 查询包装器
     * @param unitIdGetter 实体 unitId 的 getter 引用（如 BizExamGroup::getUnitId）
     */
    public static <T> void applyUnitFilter(LambdaQueryWrapper<T> wrapper,
                                            SFunction<T, Long> unitIdGetter) {
        String dataScope = DataScopeContext.getDataScope();
        Long scopeId = DataScopeContext.getScopeId();

        if ("ALL".equals(dataScope) || scopeId == null || scopeId == 0L) {
            return; // 全部数据范围，不过滤
        }

        if ("UNIT".equals(dataScope)) {
            wrapper.eq(unitIdGetter, scopeId);
        } else if ("ORG".equals(dataScope)) {
            // ORG范围：用预解析的 unitId 过滤（org所属单位的数据）
            Long unitId = DataScopeContext.getUnitId();
            if (unitId != null) {
                wrapper.eq(unitIdGetter, unitId);
            }
        }
    }

    /**
     * 为查询同时支持 orgId 过滤（ORG 类型时精确到组织）
     *
     * @param wrapper      MyBatis-Plus 查询包装器
     * @param unitIdGetter 实体 unitId 的 getter 引用
     * @param orgIdGetter  实体 orgId 的 getter 引用
     */
    public static <T> void applyFilter(LambdaQueryWrapper<T> wrapper,
                                        SFunction<T, Long> unitIdGetter,
                                        SFunction<T, Long> orgIdGetter) {
        String dataScope = DataScopeContext.getDataScope();
        Long scopeId = DataScopeContext.getScopeId();

        if ("ALL".equals(dataScope) || scopeId == null || scopeId == 0L) {
            return;
        }

        if ("UNIT".equals(dataScope)) {
            wrapper.eq(unitIdGetter, scopeId);
        } else if ("ORG".equals(dataScope)) {
            if (orgIdGetter != null) {
                wrapper.eq(orgIdGetter, scopeId);
            }
        }
    }

    /**
     * 检查当前请求是否为只读模式（系统管理员限定范围时）
     */
    public static boolean isReadOnly() {
        return DataScopeContext.isReadOnly();
    }

    /**
     * 获取当前用户的单位ID（用于新增数据时自动填入）
     * UNIT 类型返回 scopeId
     * ORG 类型返回预解析的 unitId
     * ALL 类型返回 null
     */
    public static Long getAutoFillUnitId() {
        return DataScopeContext.getCurrentUnitId();
    }
}
