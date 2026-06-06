package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.context.DataScopeContext;
import com.ccerphr.assessment.entity.SysNotification;
import com.ccerphr.assessment.service.SysNotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/notification")
public class SysNotificationController {

    private final SysNotificationService notificationService;

    public SysNotificationController(SysNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/unread")
    public Result<List<SysNotification>> getUnread() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }
        List<SysNotification> list = notificationService.getUnreadByUserId(userId);
        return Result.success(list);
    }

    @GetMapping("/read")
    public Result<List<SysNotification>> getRead() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }
        List<SysNotification> list = notificationService.getReadByUserId(userId);
        return Result.success(list);
    }

    @PutMapping("/{id}/mark-read")
    public Result<Void> markRead(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }
        notificationService.markAsRead(id, userId);
        return Result.success();
    }

    @GetMapping("/unread-count")
    public Result<Long> getUnreadCount() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.success(0L);
        }
        long count = notificationService.getUnreadCount(userId);
        return Result.success(count);
    }

    /**
     * 获取当前用户ID：优先从 DataScopeContext 获取，
     * 若不存在则从 Spring Security 的 JWT 认证信息中提取
     */
    private Long getCurrentUserId() {
        // 优先从 DataScopeContext 获取
        DataScopeContext.DataScopeInfo info = DataScopeContext.get();
        if (info != null && info.getUserId() != null) {
            return info.getUserId();
        }
        // Fallback: 从 JWT token 认证的 SecurityContext 中获取
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
            try {
                return Long.parseLong(auth.getName());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
