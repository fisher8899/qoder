package com.ccerphr.assessment.context;

import lombok.Data;

/**
 * 数据范围上下文 - 基于 ThreadLocal 存储当前请求的权限信息
 */
public class DataScopeContext {

    private static final ThreadLocal<DataScopeInfo> CONTEXT = new ThreadLocal<>();

    /**
     * 数据范围信息
     */
    @Data
    public static class DataScopeInfo {
        /** 当前角色编码 */
        private String roleCode;
        /** 数据范围类型：ALL / UNIT / ORG */
        private String dataScope;
        /** 范围ID（单位ID或组织ID，ALL时为0） */
        private Long scopeId;
        /** 范围名称 */
        private String scopeName;
        /** 当前用户ID */
        private Long userId;
        /** ORG范围时存放该org所属的unit_id，UNIT范围时等于scopeId */
        private Long unitId;
    }

    /**
     * 设置当前请求的数据范围上下文
     */
    public static void set(DataScopeInfo info) {
        CONTEXT.set(info);
    }

    /**
     * 获取当前数据范围信息
     */
    public static DataScopeInfo get() {
        return CONTEXT.get();
    }

    /**
     * 清除上下文（在请求结束时调用）
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 获取当前数据范围类型
     */
    public static String getDataScope() {
        DataScopeInfo info = CONTEXT.get();
        return info != null ? info.getDataScope() : "ALL";
    }

    /**
     * 获取当前范围ID
     */
    public static Long getScopeId() {
        DataScopeInfo info = CONTEXT.get();
        return info != null ? info.getScopeId() : 0L;
    }

    /**
     * 获取当前角色编码
     */
    public static String getRoleCode() {
        DataScopeInfo info = CONTEXT.get();
        return info != null ? info.getRoleCode() : null;
    }

    /**
     * 判断是否为全部数据范围
     */
    public static boolean isAll() {
        return "ALL".equals(getDataScope());
    }

    /**
     * 判断是否为单位级数据范围
     */
    public static boolean isUnit() {
        return "UNIT".equals(getDataScope());
    }

    /**
     * 判断是否为组织级数据范围
     */
    public static boolean isOrg() {
        return "ORG".equals(getDataScope());
    }

    /**
     * 判断当前用户是否为只读模式
     * 系统管理员(ADMIN)限定到具体单位/组织时，仅可查看不可修改业务数据
     */
    public static boolean isReadOnly() {
        DataScopeInfo info = CONTEXT.get();
        if (info == null) return false;
        // 系统管理员 且 数据范围不是 ALL 时为只读
        return "ADMIN".equals(info.getRoleCode()) && !"ALL".equals(info.getDataScope());
    }

    /**
     * 获取当前数据范围对应的单位ID
     * - UNIT 类型：等于 scopeId
     * - ORG 类型：该 org 所属的 unit_id（由 Interceptor 预解析）
     * - ALL 类型：返回 null
     */
    public static Long getUnitId() {
        DataScopeInfo info = CONTEXT.get();
        if (info == null) return null;
        return info.getUnitId();
    }

    /**
     * 获取当前用户的单位ID（用于业务数据自动填充）
     * - UNIT 类型：直接返回 scopeId
     * - ORG 类型：返回预解析的 unitId
     * - ALL 类型：返回 null（不限制）
     */
    public static Long getCurrentUnitId() {
        DataScopeInfo info = CONTEXT.get();
        if (info == null || "ALL".equals(info.getDataScope())) {
            return null;
        }
        if ("UNIT".equals(info.getDataScope())) {
            return info.getScopeId();
        }
        // ORG 类型：返回预解析的 unitId
        if ("ORG".equals(info.getDataScope())) {
            return info.getUnitId();
        }
        return null;
    }
}
