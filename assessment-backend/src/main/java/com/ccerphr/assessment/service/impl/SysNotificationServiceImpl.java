package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.entity.SysNotification;
import com.ccerphr.assessment.mapper.SysNotificationMapper;
import com.ccerphr.assessment.service.SysNotificationService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class SysNotificationServiceImpl extends ServiceImpl<SysNotificationMapper, SysNotification> implements SysNotificationService {

    @Override
    public List<SysNotification> getUnreadByUserId(Long userId) {
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotification::getRecipientUserId, userId)
               .eq(SysNotification::getIsRead, 0)
               .orderByDesc(SysNotification::getCreatedTime);
        return list(wrapper);
    }

    @Override
    public List<SysNotification> getReadByUserId(Long userId) {
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotification::getRecipientUserId, userId)
               .eq(SysNotification::getIsRead, 1)
               .orderByDesc(SysNotification::getCreatedTime);
        return list(wrapper);
    }

    @Override
    public void markAsRead(Long id, Long userId) {
        SysNotification notification = getById(id);
        if (notification == null) {
            throw new RuntimeException("通知不存在");
        }
        if (!notification.getRecipientUserId().equals(userId)) {
            throw new RuntimeException("无权操作该通知");
        }
        notification.setIsRead(1);
        updateById(notification);
    }

    @Override
    public long getUnreadCount(Long userId) {
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotification::getRecipientUserId, userId)
               .eq(SysNotification::getIsRead, 0);
        return count(wrapper);
    }

    @Override
    public IPage<SysNotification> getUnreadByUserId(Long userId, Page<SysNotification> page) {
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotification::getRecipientUserId, userId)
               .eq(SysNotification::getIsRead, 0)
               .orderByDesc(SysNotification::getCreatedTime);
        return page(page, wrapper);
    }

    @Override
    public IPage<SysNotification> getReadByUserId(Long userId, Page<SysNotification> page) {
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNotification::getRecipientUserId, userId)
               .eq(SysNotification::getIsRead, 1)
               .orderByDesc(SysNotification::getCreatedTime);
        return page(page, wrapper);
    }
}
