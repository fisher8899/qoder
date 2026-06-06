-- Preview invalid indicator owner data before deletion.
-- Invalid means the indicator has no owning unit/org, points to a missing org,
-- or its unit_id does not match the owning org's unit_id.
SELECT bid.*
FROM biz_indicator_definition bid
LEFT JOIN sys_organization org
  ON org.id = bid.org_id
 AND org.deleted = 0
WHERE bid.deleted = 0
  AND (
    bid.unit_id IS NULL
    OR bid.org_id IS NULL
    OR org.id IS NULL
    OR org.unit_id IS NULL
    OR bid.unit_id <> org.unit_id
  );

-- Soft-delete invalid indicator owner data.
-- Run the SELECT above first; then uncomment this UPDATE if the preview is correct.
-- UPDATE biz_indicator_definition bid
-- LEFT JOIN sys_organization org
--   ON org.id = bid.org_id
--  AND org.deleted = 0
-- SET bid.deleted = 1,
--     bid.updated_time = NOW()
-- WHERE bid.deleted = 0
--   AND (
--     bid.unit_id IS NULL
--     OR bid.org_id IS NULL
--     OR org.id IS NULL
--     OR org.unit_id IS NULL
--     OR bid.unit_id <> org.unit_id
--   );
