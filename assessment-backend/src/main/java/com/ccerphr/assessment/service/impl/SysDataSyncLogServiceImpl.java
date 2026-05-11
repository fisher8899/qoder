package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.entity.SysDataSyncLog;
import com.ccerphr.assessment.mapper.SysDataSyncLogMapper;
import com.ccerphr.assessment.service.SysDataSyncLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class SysDataSyncLogServiceImpl extends ServiceImpl<SysDataSyncLogMapper, SysDataSyncLog> implements SysDataSyncLogService {

    @Override
    public PageResult<SysDataSyncLog> queryHistory(long current, long size) {
        Page<SysDataSyncLog> page = page(new Page<>(current, size));
        PageResult<SysDataSyncLog> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(page.getRecords());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        return result;
    }

    @Override
    public SysDataSyncLog manualSync() {
        SysDataSyncLog log = new SysDataSyncLog();
        log.setSyncType("MANUAL");
        log.setTotalCount(0);
        log.setAddCount(0);
        log.setUpdateCount(0);
        log.setFailCount(0);
        log.setStatus("成功");
        LocalDateTime now = LocalDateTime.now();
        log.setStartTime(now);
        log.setEndTime(now);
        log.setCreatedTime(now);
        this.save(log);
        return log;
    }

    @Override
    public SysDataSyncLog getDetail(Long id) {
        SysDataSyncLog log = this.getById(id);
        if (log == null) {
            throw new BusinessException("同步记录不存在");
        }
        return log;
    }
}
