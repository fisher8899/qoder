package com.ccerphr.assessment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ccerphr.assessment.dto.MenuTreeDTO;
import com.ccerphr.assessment.entity.SysMenu;

import java.util.List;
import java.util.Map;

public interface SysMenuService extends IService<SysMenu> {

    List<Map<String, Object>> getMenuTreeByRole(String roleCode);

    List<Map<String, Object>> getMenuTreeByContext(String roleCode, String dataScope, Long scopeId);

    List<MenuTreeDTO> getMenuTree();

    List<MenuTreeDTO> getAssignableMenuTree(String roleType);

    SysMenu getDetail(Long id);

    void addMenu(SysMenu menu);

    void updateMenu(SysMenu menu);

    void deleteMenu(Long id);
}
