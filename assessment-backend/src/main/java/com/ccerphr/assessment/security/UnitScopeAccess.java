package com.ccerphr.assessment.security;

import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.context.DataScopeContext;

public final class UnitScopeAccess {

    private UnitScopeAccess() {
    }

    public static void requireAdminOrUnitScope() {
        String roleCode = DataScopeContext.getRoleCode();
        String dataScope = DataScopeContext.getDataScope();
        if ("ADMIN".equals(roleCode)) {
            return;
        }
        if ("FIN_ADMIN".equals(roleCode) && "UNIT".equals(dataScope)) {
            return;
        }
        throw new BusinessException(403, "Access denied: ADMIN or FIN_ADMIN with UNIT scope is required");
    }

    public static void requireAdminOrFinAdmin() {
        String roleCode = DataScopeContext.getRoleCode();
        if ("ADMIN".equals(roleCode) || "FIN_ADMIN".equals(roleCode)) {
            return;
        }
        throw new BusinessException(403, "Access denied: ADMIN or FIN_ADMIN role is required");
    }

    public static void requireIndicatorEditor() {
        String roleCode = DataScopeContext.getRoleCode();
        if ("ADMIN".equals(roleCode) || "FIN_ADMIN".equals(roleCode) || "DEPT_ADMIN".equals(roleCode)) {
            return;
        }
        throw new BusinessException(403, "Access denied: indicator editor role is required");
    }
}
