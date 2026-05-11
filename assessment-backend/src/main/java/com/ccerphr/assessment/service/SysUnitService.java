package com.ccerphr.assessment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.UnitQueryDTO;
import com.ccerphr.assessment.entity.SysUnit;

public interface SysUnitService extends IService<SysUnit> {

    PageResult<SysUnit> queryPage(UnitQueryDTO query);

    SysUnit getDetail(Long id);

    void addUnit(SysUnit unit);

    void updateUnit(SysUnit unit);

    void toggleStatus(Long id);

    void deleteUnit(Long id);
}
