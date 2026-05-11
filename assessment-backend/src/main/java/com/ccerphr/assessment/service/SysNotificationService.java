package com.ccerphr.assessment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ccerphr.assessment.entity.SysNotification;
import java.util.List;

public interface SysNotificationService extends IService<SysNotification> {
    List<SysNotification> getUnreadByUserId(Long userId);
    List<SysNotification> getReadByUserId(Long userId);
    void markAsRead(Long id);
    long getUnreadCount(Long userId);
}
