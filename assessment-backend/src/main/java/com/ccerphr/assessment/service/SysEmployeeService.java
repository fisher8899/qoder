package com.ccerphr.assessment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.EmployeeQueryDTO;
import com.ccerphr.assessment.entity.SysEmployee;

import java.util.List;

public interface SysEmployeeService extends IService<SysEmployee> {

    PageResult<SysEmployee> listByPage(EmployeeQueryDTO query);

    List<SysEmployee> listAll();

    SysEmployee getDetail(Long id);

    void add(SysEmployee employee);

    void update(SysEmployee employee);

    void delete(Long id);
}
