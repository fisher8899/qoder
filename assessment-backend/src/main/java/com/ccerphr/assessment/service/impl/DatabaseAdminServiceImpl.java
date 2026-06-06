package com.ccerphr.assessment.service.impl;

import com.ccerphr.assessment.common.BusinessException;
import com.ccerphr.assessment.common.PageResult;
import com.ccerphr.assessment.dto.DatabaseColumnDTO;
import com.ccerphr.assessment.dto.DatabaseQueryDTO;
import com.ccerphr.assessment.dto.DatabaseRowUpdateDTO;
import com.ccerphr.assessment.dto.DatabaseTableDTO;
import com.ccerphr.assessment.dto.DatabaseTablePageDTO;
import com.ccerphr.assessment.service.DatabaseAdminService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DatabaseAdminServiceImpl implements DatabaseAdminService {

    private static final Set<String> ALLOWED_TABLES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "sys_unit",
            "sys_leader",
            "sys_user_permission",
            "sys_organization",
            "sys_indicator_category",
            "sys_menu",
            "sys_role",
            "sys_role_menu",
            "sys_data_sync_log",
            "biz_exam_group",
            "biz_exam_group_member",
            "biz_indicator_definition",
            "biz_indicator_sub_category",
            "biz_self_evaluation",
            "biz_peer_evaluation",
            "biz_review_score",
            "biz_monthly_score",
            "biz_appeal",
            "biz_appeal_attachment",
            "sys_dict",
            "sys_operation_log",
            "sys_user",
            "sys_employee",
            "sys_user_role",
            "biz_indicator_org",
            "sys_notification",
            "biz_indicator_leader",
            "sys_role_child"
    )));

    private static final Set<String> NON_EDITABLE_COLUMNS = Set.of("id", "created_time", "updated_time", "deleted");
    private static final String LOOKUP_COLUMN_SUFFIX = "_display";
    private static final Map<String, LookupConfig> LOOKUP_CONFIGS = buildLookupConfigs();

    private final JdbcTemplate jdbcTemplate;

    public DatabaseAdminServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DatabaseTableDTO> listTables() {
        String sql = """
                SELECT table_name, table_comment
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN (%s)
                ORDER BY table_name
                """.formatted(buildInClause(ALLOWED_TABLES.size()));
        return jdbcTemplate.query(sql, tableRowMapper(), ALLOWED_TABLES.toArray());
    }

    @Override
    public DatabaseTablePageDTO queryTable(DatabaseQueryDTO queryDTO) {
        String tableName = normalizeTableName(queryDTO.getTableName());
        long current = queryDTO.getCurrent() <= 0 ? 1 : queryDTO.getCurrent();
        long size = queryDTO.getSize() <= 0 ? 10 : Math.min(queryDTO.getSize(), 50);
        long offset = (current - 1) * size;

        List<DatabaseColumnDTO> columns = loadColumns(tableName);
        if (columns.isEmpty()) {
            throw new BusinessException("未找到表字段定义");
        }

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + wrap(tableName), Long.class);
        List<Map<String, Object>> records = jdbcTemplate.query(
                "SELECT * FROM " + wrap(tableName) + " ORDER BY id DESC LIMIT ? OFFSET ?",
                this::mapRows,
                size,
                offset
        );
        enrichLookupColumns(tableName, columns, records);

        PageResult<Map<String, Object>> page = new PageResult<>();
        page.setCurrent(current);
        page.setSize(size);
        page.setTotal(total == null ? 0L : total);
        page.setRecords(records);

        DatabaseTablePageDTO result = new DatabaseTablePageDTO();
        result.setTableName(tableName);
        result.setTableComment(loadTableComment(tableName));
        result.setColumns(columns);
        result.setPage(page);
        return result;
    }

    @Override
    public void updateRow(DatabaseRowUpdateDTO updateDTO) {
        String tableName = normalizeTableName(updateDTO.getTableName());
        if (updateDTO.getId() == null || updateDTO.getId() <= 0) {
            throw new BusinessException("主键ID不能为空");
        }
        Map<String, Object> values = updateDTO.getValues();
        if (values == null || values.isEmpty()) {
            throw new BusinessException("没有可更新的数据");
        }

        List<DatabaseColumnDTO> columns = loadColumns(tableName);
        Map<String, DatabaseColumnDTO> columnMap = columns.stream()
                .collect(Collectors.toMap(DatabaseColumnDTO::getColumnName, item -> item, (a, b) -> a, LinkedHashMap::new));

        List<String> assignments = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String columnName = entry.getKey();
            DatabaseColumnDTO column = columnMap.get(columnName);
            if (column == null) {
                throw new BusinessException("字段不存在: " + columnName);
            }
            if (!column.isEditable()) {
                continue;
            }
            assignments.add(wrap(columnName) + " = ?");
            params.add(normalizeValue(entry.getValue()));
        }

        if (columnMap.containsKey("updated_time")) {
            assignments.add("`updated_time` = NOW()");
        }

        if (assignments.isEmpty()) {
            throw new BusinessException("没有可更新的字段");
        }

        params.add(updateDTO.getId());
        String sql = "UPDATE " + wrap(tableName) + " SET " + String.join(", ", assignments) + " WHERE `id` = ?";
        int affected = jdbcTemplate.update(sql, params.toArray());
        if (affected == 0) {
            throw new BusinessException("更新失败，记录不存在");
        }
    }

    @Override
    public void deleteRow(String tableName, Long id) {
        String normalizedTable = normalizeTableName(tableName);
        if (id == null || id <= 0) {
            throw new BusinessException("主键ID不能为空");
        }

        if (!hasDeletedColumn(normalizedTable)) {
            throw new BusinessException("该表未支持逻辑删除，禁止通过数据管理删除记录");
        }

        String sql = "UPDATE " + wrap(normalizedTable) + " SET `deleted` = 1"
                + (hasColumn(normalizedTable, "updated_time") ? ", `updated_time` = NOW()" : "")
                + " WHERE `id` = ? AND `deleted` = 0";
        int affected = jdbcTemplate.update(sql, id);
        if (affected == 0) {
            throw new BusinessException("删除失败，记录不存在或已删除");
        }
    }

    private boolean hasColumn(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    private String normalizeTableName(String tableName) {
        if (!StringUtils.hasText(tableName)) {
            throw new BusinessException("请选择数据表");
        }
        String normalized = tableName.trim().toLowerCase();
        if (!ALLOWED_TABLES.contains(normalized)) {
            throw new BusinessException("不允许访问该数据表: " + tableName);
        }
        return normalized;
    }

    private String loadTableComment(String tableName) {
        String sql = """
                SELECT table_comment
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """;
        String comment = jdbcTemplate.queryForObject(sql, String.class, tableName);
        return comment == null ? "" : comment;
    }

    private List<DatabaseColumnDTO> loadColumns(String tableName) {
        String sql = """
                SELECT column_name, data_type, column_comment, column_key
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                ORDER BY ordinal_position
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            DatabaseColumnDTO dto = new DatabaseColumnDTO();
            String columnName = rs.getString("column_name");
            dto.setColumnName(columnName);
            dto.setColumnType(rs.getString("data_type"));
            dto.setColumnComment(rs.getString("column_comment"));
            dto.setPrimaryKey(Objects.equals("PRI", rs.getString("column_key")));
            dto.setEditable(!NON_EDITABLE_COLUMNS.contains(columnName));
            return dto;
        }, tableName);
    }

    private List<Map<String, Object>> mapRows(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(metaData.getColumnLabel(i), rs.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }

    private void enrichLookupColumns(String tableName, List<DatabaseColumnDTO> columns, List<Map<String, Object>> records) {
        if (records.isEmpty() || columns.isEmpty()) {
            return;
        }

        Set<String> physicalColumns = columns.stream()
                .map(DatabaseColumnDTO::getColumnName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<DatabaseColumnDTO> enrichedColumns = new ArrayList<>();

        for (DatabaseColumnDTO column : columns) {
            enrichedColumns.add(column);

            LookupConfig config = LOOKUP_CONFIGS.get(column.getColumnName());
            if (config == null || !config.supports(tableName)) {
                continue;
            }

            String displayColumnName = config.displayColumnName();
            if (physicalColumns.contains(displayColumnName) || physicalColumns.contains(config.nameLikeColumnName())) {
                continue;
            }

            Map<Long, String> lookupValues = loadLookupValues(config, collectLookupIds(records, column.getColumnName()));
            if (lookupValues.isEmpty()) {
                continue;
            }

            for (Map<String, Object> row : records) {
                Long id = toLong(row.get(column.getColumnName()));
                if (id == null) {
                    row.put(displayColumnName, null);
                    continue;
                }
                row.put(displayColumnName, lookupValues.getOrDefault(id, null));
            }

            enrichedColumns.add(buildDisplayColumn(column, config));
            physicalColumns.add(displayColumnName);
        }

        columns.clear();
        columns.addAll(enrichedColumns);
    }

    private Set<Long> collectLookupIds(List<Map<String, Object>> records, String columnName) {
        return records.stream()
                .map(row -> toLong(row.get(columnName)))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<Long, String> loadLookupValues(LookupConfig config, Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        String sql = "SELECT id, " + wrap(config.displayField()) + " AS display_value FROM " + wrap(config.tableName())
                + " WHERE id IN (" + buildInClause(ids.size()) + ")" + buildDeletedPredicate(config.tableName());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, ids.toArray());
        Map<Long, String> values = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long id = toLong(row.get("id"));
            if (id == null) {
                continue;
            }
            Object value = row.get("display_value");
            values.put(id, value == null ? null : String.valueOf(value));
        }
        return values;
    }

    private String buildDeletedPredicate(String tableName) {
        return hasDeletedColumn(tableName) ? " AND deleted = 0" : "";
    }

    private boolean hasDeletedColumn(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = 'deleted'
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    private DatabaseColumnDTO buildDisplayColumn(DatabaseColumnDTO sourceColumn, LookupConfig config) {
        DatabaseColumnDTO dto = new DatabaseColumnDTO();
        dto.setColumnName(config.displayColumnName());
        dto.setColumnType("varchar");
        dto.setColumnComment(buildDisplayComment(sourceColumn, config));
        dto.setPrimaryKey(false);
        dto.setEditable(false);
        return dto;
    }

    private String buildDisplayComment(DatabaseColumnDTO sourceColumn, LookupConfig config) {
        String sourceComment = StringUtils.hasText(sourceColumn.getColumnComment())
                ? sourceColumn.getColumnComment()
                : sourceColumn.getColumnName();
        return sourceComment + "说明（" + config.label() + "）";
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Map<String, LookupConfig> buildLookupConfigs() {
        Map<String, LookupConfig> configs = new LinkedHashMap<>();
        registerLookup(configs, "unit_id", "sys_unit", "unit_name", "单位名称");
        registerLookup(configs, "org_id", "sys_organization", "org_name", "部门名称");
        registerLookup(configs, "dept_id", "sys_organization", "org_name", "部门名称");
        registerLookup(configs, "evaluator_org_id", "sys_organization", "org_name", "评估部门名称");
        registerLookup(configs, "target_org_id", "sys_organization", "org_name", "被评估部门名称");
        registerLookup(configs, "appeal_org_id", "sys_organization", "org_name", "申诉部门名称");
        registerLookup(configs, "scorer_org_id", "sys_organization", "org_name", "打分部门名称");
        registerLookup(configs, "role_id", "sys_role", "role_name", "职责名称");
        registerLookup(configs, "child_role_id", "sys_role", "role_name", "子职责名称");
        registerLookup(configs, "menu_id", "sys_menu", "menu_name", "菜单名称");
        registerLookup(configs, "parent_id", "sys_menu", "menu_name", "父菜单名称", Set.of("sys_menu"));
        registerLookup(configs, "user_id", "sys_user", "real_name", "用户姓名");
        registerLookup(configs, "leader_id", "sys_leader", "leader_name", "领导姓名");
        registerLookup(configs, "employee_id", "sys_employee", "employee_name", "人员姓名");
        registerLookup(configs, "exam_group_id", "biz_exam_group", "group_name", "考核组名称");
        registerLookup(configs, "category_id", "sys_indicator_category", "category_name", "指标大类名称");
        registerLookup(configs, "sub_category_id", "biz_indicator_sub_category", "sub_category_name", "指标小类名称");
        return Collections.unmodifiableMap(configs);
    }

    private static void registerLookup(Map<String, LookupConfig> configs, String columnName, String tableName,
                                       String displayField, String label) {
        registerLookup(configs, columnName, tableName, displayField, label, Collections.emptySet());
    }

    private static void registerLookup(Map<String, LookupConfig> configs, String columnName, String tableName,
                                       String displayField, String label, Set<String> supportedTables) {
        configs.put(columnName, new LookupConfig(columnName, tableName, displayField, label, supportedTables));
    }

    private record LookupConfig(String sourceColumnName, String tableName, String displayField, String label,
                                Set<String> supportedTables) {
        boolean supports(String currentTableName) {
            return supportedTables == null || supportedTables.isEmpty() || supportedTables.contains(currentTableName);
        }

        String displayColumnName() {
            return sourceColumnName + LOOKUP_COLUMN_SUFFIX;
        }

        String nameLikeColumnName() {
            if (sourceColumnName.endsWith("_id")) {
                return sourceColumnName.substring(0, sourceColumnName.length() - 3) + "_name";
            }
            return sourceColumnName + "_name";
        }
    }

    private Object normalizeValue(Object value) {
        return value;
    }

    private String wrap(String name) {
        if (name == null) {
            throw new BusinessException("标识符不能为空");
        }
        // 防御性校验：标识符仅允许字母、数字、下划线，杜绝反引号注入。
        // 表名/列名来源于白名单 + information_schema 查询，正常情况均符合。
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new BusinessException("非法标识符: " + name);
        }
        return "`" + name.replace("`", "``") + "`";
    }

    private String buildInClause(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private RowMapper<DatabaseTableDTO> tableRowMapper() {
        return (rs, rowNum) -> new DatabaseTableDTO(
                rs.getString("table_name"),
                rs.getString("table_comment")
        );
    }
}
