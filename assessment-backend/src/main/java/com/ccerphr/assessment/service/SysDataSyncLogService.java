package com.ccerphr.assessment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.entity.SysDataSyncLog;

public interface SysDataSyncLogService extends IService<SysDataSyncLog> {

    PageResult<SysDataSyncLog> queryHistory(long current, long size);

    SysDataSyncLog manualSync();

    SysDataSyncLog getDetail(Long id);
}
