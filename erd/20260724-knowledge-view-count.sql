BEGIN;

ALTER TABLE knowledge_share
    ADD COLUMN IF NOT EXISTS view_count BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN knowledge_share.view_count IS '게시글 상세 조회수';

COMMIT;
