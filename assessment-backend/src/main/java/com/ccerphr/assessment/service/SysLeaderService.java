package com.ccerphr.assessment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.LeaderQueryDTO;
import com.ccerphr.assessment.entity.SysLeader;

public interface SysLeaderService extends IService<SysLeader> {

    PageResult<SysLeader> queryPage(LeaderQueryDTO query);

    SysLeader getDetail(Long id);

    void addLeader(SysLeader leader);

    void updateLeader(SysLeader leader);

    void deleteLeader(Long id);
}
