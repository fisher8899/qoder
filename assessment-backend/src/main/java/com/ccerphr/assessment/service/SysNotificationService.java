package com.ccerphr.assessment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ccerphr.assessment.entity.SysNotification;
import java.util.List;

public interface SysNotificationService extends IService<SysNotification> {
    List<SysNotification> getUnreadByUserId(Long userId);
    List<SysNotification> getReadByUserId(Long userId);
    void markAsRead(Long id, Long userId);
    long getUnreadCount(Long userId);

    // P2: 分页支持
    IPage<SysNotification> getUnreadByUserId(Long userId, Page<SysNotification> page);
    IPage<SysNotification> getReadByUserId(Long userId, Page<SysNotification> page);
}
