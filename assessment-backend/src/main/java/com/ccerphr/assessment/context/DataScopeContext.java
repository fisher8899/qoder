package com.ccerphr.assessment.context;

import lombok.Data;

import java.util.Collections;
import java.util.List;

public class DataScopeContext {

    private static final ThreadLocal<DataScopeInfo> CONTEXT = new ThreadLocal<>();

    @Data
    public static class DataScopeInfo {
        private String roleCode;
        private String dataScope;
        private Long scopeId;
        private String scopeName;
        private Long userId;
        private Long unitId;
        private List<Long> visibleUnitIds;
        private List<Long> visibleOrgIds;
    }

    public static void set(DataScopeInfo info) {
        CONTEXT.set(info);
    }

    public static DataScopeInfo get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static String getDataScope() {
        return requireContext().getDataScope();
    }

    public static Long getScopeId() {
        Long scopeId = requireContext().getScopeId();
        return scopeId != null ? scopeId : 0L;
    }

    public static String getRoleCode() {
        return requireContext().getRoleCode();
    }

    public static boolean isAll() {
        return "ALL".equals(getDataScope());
    }

    public static boolean isUnit() {
        return "UNIT".equals(getDataScope());
    }

    public static boolean isOrg() {
        return "ORG".equals(getDataScope());
    }

    public static boolean isReadOnly() {
        DataScopeInfo info = CONTEXT.get();
        if (info == null) {
            return false;
        }
        return "ADMIN".equals(info.getRoleCode()) && !"ALL".equals(info.getDataScope());
    }

    public static Long getUnitId() {
        DataScopeInfo info = CONTEXT.get();
        if (info == null) {
            return null;
        }
        return info.getUnitId();
    }

    public static List<Long> getVisibleUnitIds() {
        DataScopeInfo info = CONTEXT.get();
        if (info == null || info.getVisibleUnitIds() == null) {
            return Collections.emptyList();
        }
        return info.getVisibleUnitIds();
    }

    public static List<Long> getVisibleOrgIds() {
        DataScopeInfo info = CONTEXT.get();
        if (info == null || info.getVisibleOrgIds() == null) {
            return Collections.emptyList();
        }
        return info.getVisibleOrgIds();
    }

    public static Long getCurrentUnitId() {
        DataScopeInfo info = CONTEXT.get();
        if (info == null || "ALL".equals(info.getDataScope())) {
            return null;
        }
        if ("UNIT".equals(info.getDataScope())) {
            return info.getScopeId();
        }
        if ("ORG".equals(info.getDataScope())) {
            return info.getUnitId();
        }
        return null;
    }

    private static DataScopeInfo requireContext() {
        DataScopeInfo info = CONTEXT.get();
        if (info == null) {
            throw new SecurityException("Data scope context is not initialized");
        }
        return info;
    }
}
