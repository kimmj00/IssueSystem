-- Normalize legacy preventive inspection infra values to GPM.
-- Run once before removing legacy enum/display values from the application.

UPDATE issue_case
SET infra_type = 'GPM'
WHERE infra_type IN ('예방점검', '예방 점검');

DELETE FROM knowledge_share_infra legacy
WHERE legacy.infra_type IN ('예방점검', '예방 점검')
  AND EXISTS (
      SELECT 1
      FROM knowledge_share_infra normalized
      WHERE normalized.knowledge_share_id = legacy.knowledge_share_id
        AND normalized.infra_type = 'GPM'
  );

UPDATE knowledge_share_infra
SET infra_type = 'GPM'
WHERE infra_type IN ('예방점검', '예방 점검');
