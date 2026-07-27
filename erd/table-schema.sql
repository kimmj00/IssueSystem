-- issue_system table schema for ERD/import.
-- 기준: Spring JPA Entity, ElementCollection, JdbcTemplate Repository.
-- 주의: PatchHistory는 별도 테이블이 아니라 issue_case 물리 테이블을 공유한다.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS account (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(20) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    name VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS issue_case (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    infra_type VARCHAR(50) NOT NULL,
    system_name VARCHAR(100) NOT NULL,
    customer_name VARCHAR(100),
    version_info VARCHAR(50),
    status VARCHAR(30) NOT NULL,
    symptom_summary VARCHAR(300) NOT NULL,
    symptom_detail TEXT NOT NULL,
    cause_detail TEXT,
    action_detail TEXT,
    tags VARCHAR(200),
    author_name VARCHAR(100) NOT NULL,
    category VARCHAR(50),
    deployment_version VARCHAR(50),
    completed_date DATE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS knowledge_share (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    customer_name VARCHAR(100),
    author_name VARCHAR(100) NOT NULL,
    created_by_account_id BIGINT,
    attachment_name VARCHAR(255),
    content TEXT NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_knowledge_share_account
        FOREIGN KEY (created_by_account_id)
        REFERENCES account (id)
);

CREATE TABLE IF NOT EXISTS knowledge_share_infra (
    knowledge_share_id BIGINT NOT NULL,
    infra_type VARCHAR(50) NOT NULL,
    CONSTRAINT pk_knowledge_share_infra PRIMARY KEY (knowledge_share_id, infra_type),
    CONSTRAINT fk_knowledge_share_infra_share
        FOREIGN KEY (knowledge_share_id)
        REFERENCES knowledge_share (id)
);

CREATE TABLE IF NOT EXISTS knowledge_share_attachment (
    id BIGSERIAL PRIMARY KEY,
    knowledge_share_id BIGINT NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(255) NOT NULL,
    stored_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_knowledge_share_attachment_share
        FOREIGN KEY (knowledge_share_id)
        REFERENCES knowledge_share (id)
);

CREATE TABLE IF NOT EXISTS patch_history_upload_log (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    saved_count INTEGER NOT NULL,
    excluded_count INTEGER NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS work_report_upload (
    id BIGSERIAL PRIMARY KEY,
    original_file_name VARCHAR(255) NOT NULL,
    report_week VARCHAR(50),
    uploaded_by VARCHAR(100),
    project_count INTEGER NOT NULL,
    maintenance_count INTEGER NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS work_project_history (
    id BIGSERIAL PRIMARY KEY,
    upload_id BIGINT NOT NULL,
    row_no INTEGER NOT NULL,
    excel_no VARCHAR(30),
    sales_rep VARCHAR(100),
    client_name VARCHAR(255) NOT NULL,
    scope TEXT,
    oz VARCHAR(20),
    dashboard VARCHAR(20),
    apm VARCHAR(20),
    location VARCHAR(100),
    start_date VARCHAR(50),
    project_scale VARCHAR(100),
    executors VARCHAR(255),
    visits DOUBLE PRECISION,
    md DOUBLE PRECISION,
    progress_logs TEXT,
    remaining_issues TEXT,
    site_code VARCHAR(100),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_work_project_history_upload
        FOREIGN KEY (upload_id)
        REFERENCES work_report_upload (id)
);

CREATE TABLE IF NOT EXISTS work_maintenance_history (
    id BIGSERIAL PRIMARY KEY,
    upload_id BIGINT NOT NULL,
    row_no INTEGER NOT NULL,
    excel_no VARCHAR(30),
    maintenance_name VARCHAR(255) NOT NULL,
    version VARCHAR(50),
    pg_version VARCHAR(100),
    web_version VARCHAR(100),
    status_date VARCHAR(50),
    is_uploaded VARCHAR(50),
    sms_status VARCHAR(100),
    nms_status VARCHAR(100),
    oz VARCHAR(20),
    dashboard VARCHAR(20),
    siem VARCHAR(20),
    apm VARCHAR(20),
    sales_grade VARCHAR(100),
    contract_type VARCHAR(100),
    visit_type VARCHAR(100),
    cycle VARCHAR(100),
    method VARCHAR(100),
    contract_start VARCHAR(50),
    contract_end VARCHAR(50),
    visits DOUBLE PRECISION,
    md DOUBLE PRECISION,
    region VARCHAR(100),
    inspection_dates TEXT,
    progress_issues TEXT,
    sales_rep VARCHAR(100),
    main_dev VARCHAR(100),
    sub_dev VARCHAR(100),
    remarks TEXT,
    site_code VARCHAR(100),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_work_maintenance_history_upload
        FOREIGN KEY (upload_id)
        REFERENCES work_report_upload (id)
);

-- 아래 인덱스는 필수는 아니지만, 데이터가 많아졌을 때 ILIKE/유사도 검색 속도를 보완한다.
-- Render/Supabase PostgreSQL에서도 일반적으로 사용 가능하지만, 권한이 막히면 extension 생성 권한을 먼저 확인해야 한다.

CREATE INDEX IF NOT EXISTS idx_issue_case_title_trgm
    ON issue_case USING gin ((lower(title)) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_issue_case_symptom_summary_trgm
    ON issue_case USING gin ((lower(symptom_summary)) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_issue_case_tags_trgm
    ON issue_case USING gin ((lower(coalesce(tags, ''))) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_issue_case_category
    ON issue_case (category);

CREATE INDEX IF NOT EXISTS idx_issue_case_deployment_version
    ON issue_case (deployment_version);

CREATE INDEX IF NOT EXISTS idx_issue_case_completed_date
    ON issue_case (completed_date);

CREATE INDEX IF NOT EXISTS idx_knowledge_share_title_trgm
    ON knowledge_share USING gin ((lower(title)) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_knowledge_share_content_trgm
    ON knowledge_share USING gin ((left(lower(content), 1000)) gin_trgm_ops);
