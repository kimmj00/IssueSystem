-- issue_system consolidated SQL for ERD/import.
-- 기준: Spring JPA Entity, ElementCollection, JdbcTemplate Repository.
-- 포함: 최종 테이블 스키마, 인덱스, 기존 데이터 보정, 테이블/컬럼 주석.
-- 주의: PatchHistory는 별도 테이블이 아니라 issue_case 물리 테이블을 공유한다.

DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
EXCEPTION
    WHEN insufficient_privilege THEN
        RAISE EXCEPTION 'pg_trgm extension이 필요합니다. DB 관리자 계정으로 먼저 실행하세요: CREATE EXTENSION IF NOT EXISTS pg_trgm;';
    WHEN undefined_file THEN
        RAISE EXCEPTION '현재 PostgreSQL 서버에서 pg_trgm extension을 사용할 수 없습니다. 서버에 pg_trgm/postgresql-contrib 설치가 필요합니다.';
END $$;

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

-- 기존 DB에 이미 테이블이 있는 경우 CREATE TABLE IF NOT EXISTS만으로는 신규 컬럼이 추가되지 않는다.
-- 아래 보강 섹션은 과거 패치로 추가된 컬럼/제약을 같은 파일 재실행만으로 반영하기 위한 idempotent migration이다.

BEGIN;

ALTER TABLE issue_case
    ADD COLUMN IF NOT EXISTS category VARCHAR(50);

ALTER TABLE issue_case
    ADD COLUMN IF NOT EXISTS deployment_version VARCHAR(50);

ALTER TABLE issue_case
    ADD COLUMN IF NOT EXISTS completed_date DATE;

ALTER TABLE knowledge_share
    ADD COLUMN IF NOT EXISTS created_by_account_id BIGINT;

ALTER TABLE knowledge_share
    ADD COLUMN IF NOT EXISTS view_count BIGINT DEFAULT 0;

UPDATE knowledge_share
SET view_count = 0
WHERE view_count IS NULL;

ALTER TABLE knowledge_share
    ALTER COLUMN view_count SET DEFAULT 0;

ALTER TABLE knowledge_share
    ALTER COLUMN view_count SET NOT NULL;

COMMIT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_knowledge_share_account'
          AND conrelid = 'knowledge_share'::regclass
    ) THEN
        ALTER TABLE knowledge_share
            ADD CONSTRAINT fk_knowledge_share_account
            FOREIGN KEY (created_by_account_id)
            REFERENCES account (id);
    END IF;
END $$;

-- 아래 trigram 인덱스는 데이터가 많아졌을 때 ILIKE/유사도 검색 속도를 보완한다.
-- pg_trgm은 앱의 similarity() 검색에도 필요하므로 생성되지 않으면 위 블록에서 스크립트를 중단한다.

CREATE INDEX IF NOT EXISTS idx_issue_case_title_trgm
    ON issue_case USING gin ((lower(title)) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_issue_case_symptom_summary_trgm
    ON issue_case USING gin ((lower(symptom_summary)) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_issue_case_tags_trgm
    ON issue_case USING gin ((lower(coalesce(tags, ''))) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_knowledge_share_title_trgm
    ON knowledge_share USING gin ((lower(title)) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_knowledge_share_content_trgm
    ON knowledge_share USING gin ((left(lower(content), 1000)) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_issue_case_category
    ON issue_case (category);

CREATE INDEX IF NOT EXISTS idx_issue_case_deployment_version
    ON issue_case (deployment_version);

CREATE INDEX IF NOT EXISTS idx_issue_case_completed_date
    ON issue_case (completed_date);

-- 기존 예방점검 인프라 값을 GPM으로 정규화한다. 새 DB에서는 영향이 없다.

BEGIN;

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

COMMIT;

-- PostgreSQL table/column comments.
-- 실행 효과: 테이블/컬럼 설명 메타데이터만 갱신하며 데이터와 컬럼 구조는 변경하지 않는다.

BEGIN;

COMMENT ON TABLE account IS 'TC Bank 사용자 계정 테이블';
COMMENT ON COLUMN account.id IS '계정 고유 ID';
COMMENT ON COLUMN account.user_id IS '로그인 ID';
COMMENT ON COLUMN account.password_hash IS 'BCrypt 해시 비밀번호';
COMMENT ON COLUMN account.name IS '사용자 이름';
COMMENT ON COLUMN account.role IS '계정 권한(USER, ADMIN)';
COMMENT ON COLUMN account.created_at IS '가입 일시';
COMMENT ON COLUMN account.updated_at IS '마지막 수정 일시';

COMMENT ON TABLE issue_case IS '이슈/패치 이력 공통 저장 테이블';
COMMENT ON COLUMN issue_case.id IS '이슈/패치 이력 고유 ID';
COMMENT ON COLUMN issue_case.title IS '이슈 또는 패치 이력 제목';
COMMENT ON COLUMN issue_case.infra_type IS '인프라 유형. 예: EMS, DBMS, APM 등';
COMMENT ON COLUMN issue_case.system_name IS '대상 시스템명';
COMMENT ON COLUMN issue_case.customer_name IS '고객사명';
COMMENT ON COLUMN issue_case.version_info IS '제품 또는 시스템 버전 정보';
COMMENT ON COLUMN issue_case.status IS '처리 상태. 예: OPEN, RESOLVED, CLOSED';
COMMENT ON COLUMN issue_case.symptom_summary IS '증상 또는 변경 내용 요약';
COMMENT ON COLUMN issue_case.symptom_detail IS '증상 또는 패치 상세 내용';
COMMENT ON COLUMN issue_case.cause_detail IS '원인 상세 내용';
COMMENT ON COLUMN issue_case.action_detail IS '조치 상세 내용';
COMMENT ON COLUMN issue_case.tags IS '검색 및 분류용 태그';
COMMENT ON COLUMN issue_case.author_name IS '작성자명';
COMMENT ON COLUMN issue_case.category IS '패치 이력 업무 구분 또는 분류값';
COMMENT ON COLUMN issue_case.deployment_version IS '패치 이력 배포 버전';
COMMENT ON COLUMN issue_case.completed_date IS '패치 이력 완료일';
COMMENT ON COLUMN issue_case.created_at IS '최초 생성 일시';
COMMENT ON COLUMN issue_case.updated_at IS '마지막 수정 일시';

COMMENT ON TABLE knowledge_share IS '지식공유 게시글 저장 테이블';
COMMENT ON COLUMN knowledge_share.id IS '지식공유 게시글 고유 ID';
COMMENT ON COLUMN knowledge_share.title IS '지식공유 제목';
COMMENT ON COLUMN knowledge_share.customer_name IS '고객사명';
COMMENT ON COLUMN knowledge_share.author_name IS '담당자 또는 작성자명';
COMMENT ON COLUMN knowledge_share.created_by_account_id IS '지식을 등록한 계정 ID';
COMMENT ON COLUMN knowledge_share.attachment_name IS '기존 화면 호환용 첨부파일명';
COMMENT ON COLUMN knowledge_share.content IS '지식공유 본문 내용';
COMMENT ON COLUMN knowledge_share.view_count IS '게시글 상세 조회수';
COMMENT ON COLUMN knowledge_share.created_at IS '최초 생성 일시';
COMMENT ON COLUMN knowledge_share.updated_at IS '마지막 수정 일시';

COMMENT ON TABLE knowledge_share_infra IS '지식공유 게시글별 인프라 유형 매핑 테이블';
COMMENT ON COLUMN knowledge_share_infra.knowledge_share_id IS '지식공유 게시글 ID';
COMMENT ON COLUMN knowledge_share_infra.infra_type IS '선택된 인프라 유형. 예: EMS, DBMS, APM 등';

COMMENT ON TABLE knowledge_share_attachment IS '지식공유 첨부파일 메타데이터 저장 테이블';
COMMENT ON COLUMN knowledge_share_attachment.id IS '첨부파일 고유 ID';
COMMENT ON COLUMN knowledge_share_attachment.knowledge_share_id IS '첨부파일이 속한 지식공유 게시글 ID';
COMMENT ON COLUMN knowledge_share_attachment.original_file_name IS '사용자가 업로드한 원본 파일명';
COMMENT ON COLUMN knowledge_share_attachment.stored_file_name IS '서버에 저장된 확장자 없는 랜덤 파일명';
COMMENT ON COLUMN knowledge_share_attachment.stored_path IS '서버 실제 저장 경로';
COMMENT ON COLUMN knowledge_share_attachment.file_size IS '원본 파일 크기(byte)';
COMMENT ON COLUMN knowledge_share_attachment.created_at IS '최초 생성 일시';
COMMENT ON COLUMN knowledge_share_attachment.updated_at IS '마지막 수정 일시';

COMMENT ON TABLE patch_history_upload_log IS '패치 이력 엑셀 업로드 로그 테이블';
COMMENT ON COLUMN patch_history_upload_log.id IS '패치 이력 업로드 로그 고유 ID';
COMMENT ON COLUMN patch_history_upload_log.file_name IS '업로드된 원본 파일명';
COMMENT ON COLUMN patch_history_upload_log.saved_count IS '저장된 패치 이력 행 수';
COMMENT ON COLUMN patch_history_upload_log.excluded_count IS '중복 또는 유효하지 않아 제외된 행 수';
COMMENT ON COLUMN patch_history_upload_log.created_at IS '최초 생성 일시';
COMMENT ON COLUMN patch_history_upload_log.updated_at IS '마지막 수정 일시';

COMMENT ON TABLE work_report_upload IS '작업/이슈 엑셀 업로드 이력 테이블';
COMMENT ON COLUMN work_report_upload.id IS '업로드 이력 고유 ID';
COMMENT ON COLUMN work_report_upload.original_file_name IS '사용자가 업로드한 원본 엑셀 파일명';
COMMENT ON COLUMN work_report_upload.report_week IS '주간보고 기준 주차';
COMMENT ON COLUMN work_report_upload.uploaded_by IS '업로드한 사용자명';
COMMENT ON COLUMN work_report_upload.project_count IS '프로젝트 현황 시트에서 저장된 건수';
COMMENT ON COLUMN work_report_upload.maintenance_count IS '유지보수 현황 시트에서 저장된 건수';
COMMENT ON COLUMN work_report_upload.created_at IS '최초 생성 일시';
COMMENT ON COLUMN work_report_upload.updated_at IS '마지막 수정 일시';

COMMENT ON TABLE work_project_history IS '작업/이슈 프로젝트 현황 상세 테이블';
COMMENT ON COLUMN work_project_history.id IS '프로젝트 현황 행 고유 ID';
COMMENT ON COLUMN work_project_history.upload_id IS '이 행을 생성한 엑셀 업로드 이력 ID';
COMMENT ON COLUMN work_project_history.row_no IS '원본 엑셀 실제 행 번호';
COMMENT ON COLUMN work_project_history.excel_no IS '원본 엑셀 번호 컬럼 값';
COMMENT ON COLUMN work_project_history.sales_rep IS '영업 담당자';
COMMENT ON COLUMN work_project_history.client_name IS '고객사 또는 프로젝트 대상명';
COMMENT ON COLUMN work_project_history.scope IS '프로젝트 범위 또는 수행 내용';
COMMENT ON COLUMN work_project_history.oz IS 'OZ 제품 적용 여부 또는 상태';
COMMENT ON COLUMN work_project_history.dashboard IS 'Dashboard 제품 적용 여부 또는 상태';
COMMENT ON COLUMN work_project_history.apm IS 'APM 제품 적용 여부 또는 상태';
COMMENT ON COLUMN work_project_history.location IS '프로젝트 수행 지역 또는 위치';
COMMENT ON COLUMN work_project_history.start_date IS '프로젝트 시작일';
COMMENT ON COLUMN work_project_history.project_scale IS '프로젝트 규모';
COMMENT ON COLUMN work_project_history.executors IS '수행자 목록';
COMMENT ON COLUMN work_project_history.visits IS '방문 횟수';
COMMENT ON COLUMN work_project_history.md IS '투입 공수(M/D)';
COMMENT ON COLUMN work_project_history.progress_logs IS '금주 실적 및 진행 내역 원문';
COMMENT ON COLUMN work_project_history.remaining_issues IS '잔여 이슈 또는 후속 조치 사항';
COMMENT ON COLUMN work_project_history.site_code IS '사이트 코드';
COMMENT ON COLUMN work_project_history.created_at IS '최초 생성 일시';
COMMENT ON COLUMN work_project_history.updated_at IS '마지막 수정 일시';

COMMENT ON TABLE work_maintenance_history IS '작업/이슈 유지보수 현황 상세 테이블';
COMMENT ON COLUMN work_maintenance_history.id IS '유지보수 현황 행 고유 ID';
COMMENT ON COLUMN work_maintenance_history.upload_id IS '이 행을 생성한 엑셀 업로드 이력 ID';
COMMENT ON COLUMN work_maintenance_history.row_no IS '원본 엑셀 실제 행 번호';
COMMENT ON COLUMN work_maintenance_history.excel_no IS '원본 엑셀 번호 컬럼 값';
COMMENT ON COLUMN work_maintenance_history.maintenance_name IS '유지보수 고객사 또는 대상명';
COMMENT ON COLUMN work_maintenance_history.version IS '제품 또는 계약 버전';
COMMENT ON COLUMN work_maintenance_history.pg_version IS 'PG 버전';
COMMENT ON COLUMN work_maintenance_history.web_version IS 'WEB 버전';
COMMENT ON COLUMN work_maintenance_history.status_date IS '현황 기준일';
COMMENT ON COLUMN work_maintenance_history.is_uploaded IS '업로드 또는 적용 여부';
COMMENT ON COLUMN work_maintenance_history.sms_status IS 'SMS 상태';
COMMENT ON COLUMN work_maintenance_history.nms_status IS 'NMS 상태';
COMMENT ON COLUMN work_maintenance_history.oz IS 'OZ 제품 적용 여부 또는 상태';
COMMENT ON COLUMN work_maintenance_history.dashboard IS 'Dashboard 제품 적용 여부 또는 상태';
COMMENT ON COLUMN work_maintenance_history.siem IS 'SIEM 제품 적용 여부 또는 상태';
COMMENT ON COLUMN work_maintenance_history.apm IS 'APM 제품 적용 여부 또는 상태';
COMMENT ON COLUMN work_maintenance_history.sales_grade IS '영업 등급';
COMMENT ON COLUMN work_maintenance_history.contract_type IS '계약 유형';
COMMENT ON COLUMN work_maintenance_history.visit_type IS '방문 유형';
COMMENT ON COLUMN work_maintenance_history.cycle IS '점검 또는 방문 주기';
COMMENT ON COLUMN work_maintenance_history.method IS '수행 방식';
COMMENT ON COLUMN work_maintenance_history.contract_start IS '계약 시작일';
COMMENT ON COLUMN work_maintenance_history.contract_end IS '계약 종료일';
COMMENT ON COLUMN work_maintenance_history.visits IS '방문 횟수';
COMMENT ON COLUMN work_maintenance_history.md IS '투입 공수(M/D)';
COMMENT ON COLUMN work_maintenance_history.region IS '지역';
COMMENT ON COLUMN work_maintenance_history.inspection_dates IS '정기점검 월별 수행일자 원문';
COMMENT ON COLUMN work_maintenance_history.progress_issues IS '진행 이슈 또는 처리 현황';
COMMENT ON COLUMN work_maintenance_history.sales_rep IS '영업 담당자';
COMMENT ON COLUMN work_maintenance_history.main_dev IS '주 담당 개발자 또는 엔지니어';
COMMENT ON COLUMN work_maintenance_history.sub_dev IS '부 담당 개발자 또는 엔지니어';
COMMENT ON COLUMN work_maintenance_history.remarks IS '비고';
COMMENT ON COLUMN work_maintenance_history.site_code IS '사이트 코드';
COMMENT ON COLUMN work_maintenance_history.created_at IS '최초 생성 일시';
COMMENT ON COLUMN work_maintenance_history.updated_at IS '마지막 수정 일시';

COMMIT;
