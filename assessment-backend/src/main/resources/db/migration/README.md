本目录用于 Flyway 受控迁移。

当前已纳管内容：
- `V2__schema_sync.sql`：承接历史手工 schema 修正与一次性数据回填。

当前限制：
- 仓库中的 `db/schema.sql` 与 `db/data.sql` 仍是历史初始化脚本，尚未整理为标准 Flyway 基线。
- 既有环境依赖 `baseline-on-migrate` 接管，再执行 `V2__schema_sync.sql`。

后续规则：
- 新增表结构、索引、约束、一次性修数，必须新增 `V{n}__*.sql`。
- 禁止继续新增未纳管 SQL 并靠手工执行。
