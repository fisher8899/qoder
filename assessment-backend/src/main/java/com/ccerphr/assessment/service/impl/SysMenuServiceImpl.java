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
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    private static final Set<String> ALLOWED_MENU_CATEGORIES = Set.of("SYSTEM", "UNIT", "DEPT");

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;

    public SysMenuServiceImpl(SysRoleMapper sysRoleMapper, SysRoleMenuMapper sysRoleMenuMapper) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
    }

    @Override
    public List<Map<String, Object>> getMenuTreeByRole(String roleCode) {
        return getMenuTreeByContext(roleCode, null, null);
    }

    @Override
    public List<Map<String, Object>> getMenuTreeByContext(String roleCode, String dataScope, Long scopeId) {
        if (!StringUtils.hasText(roleCode)) {
            return new ArrayList<>();
        }

        SysRole role = findRole(roleCode);
        if (role == null) {
            return new ArrayList<>();
        }

        List<SysRole> matchedRoles = resolveMenuRoles(role, dataScope, scopeId);
        if (matchedRoles.isEmpty()) {
            matchedRoles.add(role);
        }

        List<Long> roleIds = matchedRoles.stream()
            .map(SysRole::getId)
            .distinct()
            .toList();

        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysRoleMenu::getRoleId, roleIds);
        List<SysRoleMenu> roleMenus = sysRoleMenuMapper.selectList(wrapper);
        if (roleMenus.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, Integer> sortCodeMap = new LinkedHashMap<>();
        for (SysRoleMenu roleMenu : roleMenus) {
            sortCodeMap.merge(
                roleMenu.getMenuId(),
                roleMenu.getSortCode() != null ? roleMenu.getSortCode() : 0,
                Math::min
            );
        }

        List<SysMenu> scopedMenus = retainMenusAndAncestors(
            filterEnabledMenus(this.listByIds(sortCodeMap.keySet())),
            role.getRoleType()
        );
        if (scopedMenus.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, List<SysMenu>> parentMap = scopedMenus.stream()
            .collect(Collectors.groupingBy(menu -> menu.getParentId() == null ? 0L : menu.getParentId()));
        return filterEmptyGroups(buildRoleMenuTree(parentMap, 0L, sortCodeMap));
    }

    @Override
    public List<MenuTreeDTO> getMenuTree() {
        return buildTreeForMenus(this.list());
    }

    @Override
    public List<MenuTreeDTO> getAssignableMenuTree(String roleType) {
        List<SysMenu> allMenus = this.list(new LambdaQueryWrapper<SysMenu>()
            .eq(SysMenu::getIsEnabled, 1));
        if (!StringUtils.hasText(roleType) || "SYSTEM".equals(roleType)) {
            return buildTreeForMenus(allMenus);
        }
        return buildTreeForMenus(retainMenusAndAncestors(allMenus, roleType));
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
        normalizeMenu(menu);
        menu.setCreatedTime(LocalDateTime.now());
        this.save(menu);
    }

    @Override
    public void updateMenu(SysMenu menu) {
        if (menu.getId() == null) {
            throw new BusinessException("菜单ID不能为空");
        }
        normalizeMenu(menu);
        this.updateById(menu);
    }

    @Override
    public void deleteMenu(Long id) {
        if (!this.removeById(id)) {
            throw new BusinessException("菜单不存在或已删除");
        }
    }

    public static boolean isMenuAllowedForRoleType(SysMenu menu, String roleType) {
        return menu != null && isMenuAllowedForRoleType(menu.getMenuCategory(), roleType);
    }

    public static boolean isMenuAllowedForRoleType(String menuCategory, String roleType) {
        if (!StringUtils.hasText(roleType) || "SYSTEM".equals(roleType)) {
            return true;
        }
        if (!StringUtils.hasText(menuCategory)) {
            return true;
        }
        return Arrays.stream(menuCategory.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(String::toUpperCase)
            .anyMatch(roleType::equals);
    }

    private SysRole findRole(String roleCode) {
        LambdaQueryWrapper<SysRole> roleQuery = new LambdaQueryWrapper<>();
        roleQuery.eq(SysRole::getRoleCode, roleCode);
        return sysRoleMapper.selectOne(roleQuery);
    }

    private List<SysRole> resolveMenuRoles(SysRole currentRole, String dataScope, Long scopeId) {
        return List.of(currentRole);
    }

    private List<SysMenu> filterEnabledMenus(List<SysMenu> menus) {
        if (menus == null || menus.isEmpty()) {
            return new ArrayList<>();
        }
        return menus.stream()
            .filter(menu -> menu.getIsEnabled() == null || menu.getIsEnabled() == 1)
            .toList();
    }

    private List<SysMenu> retainMenusAndAncestors(Collection<SysMenu> sourceMenus, String roleType) {
        if (sourceMenus == null || sourceMenus.isEmpty()) {
            return new ArrayList<>();
        }

        List<SysMenu> allMenus = new ArrayList<>(sourceMenus);
        if (!StringUtils.hasText(roleType) || "SYSTEM".equals(roleType)) {
            return allMenus;
        }

        Map<Long, SysMenu> byId = allMenus.stream()
            .collect(Collectors.toMap(SysMenu::getId, menu -> menu, (left, right) -> left, LinkedHashMap::new));
        LinkedHashMap<Long, SysMenu> retained = new LinkedHashMap<>();

        for (SysMenu menu : allMenus) {
            if (!isMenuAllowedForRoleType(menu, roleType)) {
                continue;
            }
            retainWithAncestors(menu, byId, retained);
        }
        return new ArrayList<>(retained.values());
    }

    private void retainWithAncestors(SysMenu menu, Map<Long, SysMenu> byId, Map<Long, SysMenu> retained) {
        SysMenu current = menu;
        while (current != null) {
            retained.putIfAbsent(current.getId(), current);
            Long parentId = current.getParentId();
            if (parentId == null || parentId == 0L) {
                break;
            }
            current = byId.get(parentId);
        }
    }

    private List<Map<String, Object>> buildRoleMenuTree(
        Map<Long, List<SysMenu>> parentMap,
        Long parentId,
        Map<Long, Integer> sortCodeMap
    ) {
        List<SysMenu> menus = parentMap.getOrDefault(parentId, new ArrayList<>());
        return menus.stream()
            .sorted((a, b) -> compareMenuSort(a, b, sortCodeMap))
            .map(menu -> {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", menu.getId());
                node.put("menuName", menu.getMenuName());
                node.put("menuCode", menu.getMenuCode());
                node.put("menuCategory", menu.getMenuCategory());
                node.put("menuPath", menu.getMenuPath());
                node.put("menuIcon", menu.getMenuIcon());
                node.put("parentId", menu.getParentId());
                node.put("sortCode", sortCodeMap.getOrDefault(menu.getId(), menu.getSortCode() == null ? 0 : menu.getSortCode()));
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
            String menuPath = (String) node.get("menuPath");
            if (!StringUtils.hasText(menuPath) && filteredChildren.isEmpty()) {
                continue;
            }
            result.add(node);
        }
        return result;
    }

    private List<MenuTreeDTO> buildTreeForMenus(List<SysMenu> menus) {
        Map<Long, List<SysMenu>> parentMap = menus.stream()
            .collect(Collectors.groupingBy(menu -> menu.getParentId() == null ? 0L : menu.getParentId()));
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
                dto.setMenuCategory(menu.getMenuCategory());
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

    private int compareMenuSort(SysMenu left, SysMenu right, Map<Long, Integer> sortCodeMap) {
        int sortA = sortCodeMap.getOrDefault(left.getId(), left.getSortCode() == null ? 0 : left.getSortCode());
        int sortB = sortCodeMap.getOrDefault(right.getId(), right.getSortCode() == null ? 0 : right.getSortCode());
        if (sortA == 0 && sortB == 0) {
            return left.getMenuName().compareTo(right.getMenuName());
        }
        if (sortA == 0) {
            return 1;
        }
        if (sortB == 0) {
            return -1;
        }
        int cmp = Integer.compare(sortA, sortB);
        return cmp != 0 ? cmp : left.getMenuName().compareTo(right.getMenuName());
    }

    private void normalizeMenu(SysMenu menu) {
        if (!StringUtils.hasText(menu.getMenuCategory())) {
            throw new BusinessException("菜单适用类别不能为空");
        }

        LinkedHashSet<String> normalized = Arrays.stream(menu.getMenuCategory().split(","))
            .map(item -> item == null ? "" : item.trim().toUpperCase())
            .filter(StringUtils::hasText)
            .peek(item -> {
                if (!ALLOWED_MENU_CATEGORIES.contains(item)) {
                    throw new BusinessException("菜单适用类别非法: " + item);
                }
            })
            .collect(Collectors.toCollection(LinkedHashSet::new));

        if (normalized.isEmpty()) {
            throw new BusinessException("菜单适用类别不能为空");
        }

        menu.setMenuCategory(String.join(",", normalized));
        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
    }
}
