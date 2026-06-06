package com.ccerphr.assessment.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.ccerphr.assessment.context.DataScopeContext;

import java.util.List;

public class DataScopeFilter {

    public static <T> void applyUnitFilter(LambdaQueryWrapper<T> wrapper,
                                           SFunction<T, Long> unitIdGetter) {
        String dataScope = DataScopeContext.getDataScope();
        Long scopeId = DataScopeContext.getScopeId();

        if ("ALL".equals(dataScope) || scopeId == null || scopeId == 0L) {
            return;
        }

        if ("UNIT".equals(dataScope)) {
            wrapper.eq(unitIdGetter, scopeId);
        } else if ("ORG".equals(dataScope)) {
            Long unitId = DataScopeContext.getUnitId();
            if (unitId != null) {
                wrapper.eq(unitIdGetter, unitId);
            }
        }
    }

    public static <T> void applyOrgFilter(LambdaQueryWrapper<T> wrapper,
                                          SFunction<T, Long> orgIdGetter) {
        String dataScope = DataScopeContext.getDataScope();
        Long scopeId = DataScopeContext.getScopeId();

        if ("ALL".equals(dataScope) || scopeId == null || scopeId == 0L) {
            return;
        }

        if ("UNIT".equals(dataScope)) {
            List<Long> visibleOrgIds = DataScopeContext.getVisibleOrgIds();
            if (!visibleOrgIds.isEmpty()) {
                wrapper.in(orgIdGetter, visibleOrgIds);
            } else {
                wrapper.in(orgIdGetter, List.of(-1L));
            }
        } else if ("ORG".equals(dataScope)) {
            wrapper.eq(orgIdGetter, scopeId);
        }
    }

    public static <T> void applyFilter(LambdaQueryWrapper<T> wrapper,
                                       SFunction<T, Long> unitIdGetter,
                                       SFunction<T, Long> orgIdGetter) {
        String dataScope = DataScopeContext.getDataScope();
        Long scopeId = DataScopeContext.getScopeId();

        if ("ALL".equals(dataScope) || scopeId == null || scopeId == 0L) {
            return;
        }

        if ("UNIT".equals(dataScope)) {
            List<Long> visibleOrgIds = DataScopeContext.getVisibleOrgIds();
            if (orgIdGetter != null && !visibleOrgIds.isEmpty()) {
                wrapper.in(orgIdGetter, visibleOrgIds);
            } else {
                wrapper.eq(unitIdGetter, scopeId);
            }
        } else if ("ORG".equals(dataScope)) {
            if (orgIdGetter != null) {
                wrapper.eq(orgIdGetter, scopeId);
            }
        }
    }

    public static boolean isReadOnly() {
        return DataScopeContext.isReadOnly();
    }

    public static Long getAutoFillUnitId() {
        return DataScopeContext.getCurrentUnitId();
    }
}
