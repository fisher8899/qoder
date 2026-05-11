package com.ccerphr.assessment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.OrganizationQueryDTO;
import com.ccerphr.assessment.entity.SysOrganization;

import java.util.List;

public interface SysOrganizationService extends IService<SysOrganization> {

    PageResult<SysOrganization> queryPage(OrganizationQueryDTO query);

    SysOrganization getDetail(Long id);

    List<SysOrganization> getAll();

    void addOrganization(SysOrganization organization);

    void updateOrganization(SysOrganization organization);

    void deleteOrganization(Long id);
}
