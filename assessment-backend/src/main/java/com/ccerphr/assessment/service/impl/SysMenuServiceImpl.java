package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.dto.MenuTreeDTO;
import com.ccerphr.assessment.entity.SysMenu;
import com.ccerphr.assessment.entity.SysRole;
import com.ccerphr.assessment.entity.SysRoleMenu;
import com.ccerphr.assessment.mapper.SysMenuMapper;
import com.ccerphr.assessment.mapper.SysRoleMapper;
import com.ccerphr.assessment.mapper.SysRoleMenuMapper;
import com.ccerphr.assessment.service.SysMenuService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;

    public SysMenuServiceImpl(SysRoleMapper sysRoleMapper, SysRoleMenuMapper sysRoleMenuMapper) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
    }

    @Override
    public List<Map<String, Object>> getMenuTreeByRole(String roleCode) {
        // 1. 根据roleCode查找角色
        LambdaQueryWrapper<SysRole> roleQuery = new LambdaQueryWrapper<>();
        roleQuery.eq(SysRole::getRoleCode, roleCode);
        SysRole role = sysRoleMapper.selectOne(roleQuery);
        if (role == null) {
            return new ArrayList<>();
        }

        // 2. 查找角色关联的菜单ID
        LambdaQueryWrapper<SysRoleMenu> rmQuery = new LambdaQueryWrapper<>();
        rmQuery.eq(SysRoleMenu::getRoleId, role.getId());
        List<SysRoleMenu> roleMenus = sysRoleMenuMapper.selectList(rmQuery);
        if (roleMenus.isEmpty()) {
            return new ArrayList<>();
        }
        // 构建 menuId -> sortCode 映射
        Map<Long, Integer> sortCodeMap = roleMenus.stream()
                .collect(Collectors.toMap(SysRoleMenu::getMenuId,
                        rm -> rm.getSortCode() != null ? rm.getSortCode() : 0));
        List<Long> menuIds = roleMenus.stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());

        // 3. 查询菜单记录(deleted=0由@TableLogic处理)
        List<SysMenu> menus = this.listByIds(menuIds);
        if (menus.isEmpty()) {
            return new ArrayList<>();
        }

        // 4. 构建树形结构
        Map<Long, List<SysMenu>> parentMap = menus.stream()
                .collect(Collectors.groupingBy(m -> m.getParentId() == null ? 0L : m.getParentId()));
        List<Map<String, Object>> tree = buildRoleMenuTree(parentMap, 0L, sortCodeMap);

        // 5. 过滤没有子菜单的分组节点(menuPath为空且无children)
        return filterEmptyGroups(tree);
    }

    private List<Map<String, Object>> buildRoleMenuTree(Map<Long, List<SysMenu>> parentMap, Long parentId, Map<Long, Integer> sortCodeMap) {
        List<SysMenu> menus = parentMap.getOrDefault(parentId, new ArrayList<>());
        return menus.stream()
                .sorted((a, b) -> {
                    int sortA = sortCodeMap.getOrDefault(a.getId(), 0);
                    int sortB = sortCodeMap.getOrDefault(b.getId(), 0);
                    // 都为0，按菜单名排
                    if (sortA == 0 && sortB == 0) return a.getMenuName().compareTo(b.getMenuName());
                    // sortA为0排后面
                    if (sortA == 0) return 1;
                    // sortB为0排后面
                    if (sortB == 0) return -1;
                    // 都不为0，按sortCode升序，相同按菜单名
                    int cmp = Integer.compare(sortA, sortB);
                    return cmp != 0 ? cmp : a.getMenuName().compareTo(b.getMenuName());
                })
                .map(menu -> {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", menu.getId());
                    node.put("menuName", menu.getMenuName());
                    node.put("menuCode", menu.getMenuCode());
                    node.put("menuPath", menu.getMenuPath());
                    node.put("menuIcon", menu.getMenuIcon());
                    node.put("parentId", menu.getParentId());
                    node.put("sortCode", sortCodeMap.getOrDefault(menu.getId(), 0));
                    node.put("children", buildRoleMenuTree(parentMap, menu.getId(), sortCodeMap));
                    return node;
                })
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> filterEmptyGroups(List<Map<String, Object>> tree) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> node : tree) {
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
            List<Map<String, Object>> filteredChildren = filterEmptyGroups(children);
            node.put("children", filteredChildren);
            // 如果menuPath为空且没有子节点，则过滤掉
            String menuPath = (String) node.get("menuPath");
            if ((menuPath == null || menuPath.isEmpty()) && filteredChildren.isEmpty()) {
                continue;
            }
            result.add(node);
        }
        return result;
    }

    @Override
    public List<MenuTreeDTO> getMenuTree() {
        List<SysMenu> allMenus = this.list();
        Map<Long, List<SysMenu>> parentMap = allMenus.stream()
                .collect(Collectors.groupingBy(m -> m.getParentId() == null ? 0L : m.getParentId()));
        return buildTree(parentMap, 0L);
    }

    private List<MenuTreeDTO> buildTree(Map<Long, List<SysMenu>> parentMap, Long parentId) {
        List<SysMenu> menus = parentMap.getOrDefault(parentId, new ArrayList<>());
        return menus.stream()
                .sorted(Comparator.comparing(SysMenu::getSortCode, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(menu -> {
                    MenuTreeDTO dto = new MenuTreeDTO();
                    dto.setId(menu.getId());
                    dto.setMenuName(menu.getMenuName());
                    dto.setMenuCode(menu.getMenuCode());
                    dto.setParentId(menu.getParentId());
                    dto.setMenuPath(menu.getMenuPath());
                    dto.setMenuIcon(menu.getMenuIcon());
                    dto.setSortCode(menu.getSortCode());
                    dto.setIsEnabled(menu.getIsEnabled());
                    dto.setChildren(buildTree(parentMap, menu.getId()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public SysMenu getDetail(Long id) {
        SysMenu menu = this.getById(id);
        if (menu == null) {
            throw new BusinessException("菜单不存在");
        }
        return menu;
    }

    @Override
    public void addMenu(SysMenu menu) {
        menu.setCreatedTime(LocalDateTime.now());
        this.save(menu);
    }

    @Override
    public void updateMenu(SysMenu menu) {
        if (menu.getId() == null) {
            throw new BusinessException("菜单ID不能为空");
        }
        this.updateById(menu);
    }

    @Override
    public void deleteMenu(Long id) {
        if (!this.removeById(id)) {
            throw new BusinessException("菜单不存在或已删除");
        }
    }
}
