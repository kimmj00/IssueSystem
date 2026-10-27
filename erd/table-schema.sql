-- issue_system 최초 구축용 통합 SQL (PostgreSQL 13 이상)
-- 준비/실행 순서: erd/README.md. DB와 로그인 계정 생성은 관리자 단계에서 먼저 수행한다.
-- DB 소유자인 앱 계정으로 실행한다. 실행 계정과 같은 이름의 스키마를 생성/사용한다.
-- 실행: psql -X -h 127.0.0.1 -p 25432 -U issue -d issue_system -W -v ON_ERROR_STOP=1 -f /home/issue/erd/table-schema.sql
-- psql 기본 자동 커밋 상태로 파일 실행한다. 전체 파일을 BEGIN 또는 --single-transaction(-1)으로 감싸지 않는다.
-- 01~07은 한 트랜잭션, 08 검색 데이터 반영은 500행씩 커밋한다. 빈 DB에서는 즉시 완료된다.
-- 재실행 시 테이블/원문을 삭제하지 않는다. 데이터가 있는 DB에서는 앱 쓰기를 중지한 상태로 실행한다.
-- PatchHistory와 IssueCase는 같은 issue_case 물리 테이블을 공유한다.

BEGIN;

-- 01. 앱 전용 스키마와 확장 준비
DO $$
BEGIN
    EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I AUTHORIZATION %I', current_user, current_user);
    PERFORM set_config('search_path', format('%I, public', current_user), false);
    RAISE NOTICE '초기 구축 시작: DB=%, 계정=%, 스키마=%', current_database(), current_user, current_schema();
END $$;

DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
EXCEPTION
    WHEN insufficient_privilege THEN
        RAISE EXCEPTION 'pg_trgm extension이 필요합니다. DB 관리자 계정으로 먼저 실행하세요: CREATE EXTENSION IF NOT EXISTS pg_trgm;';
    WHEN undefined_file THEN
        RAISE EXCEPTION '현재 PostgreSQL 서버에서 pg_trgm extension을 사용할 수 없습니다. 서버에 pg_trgm/postgresql-contrib 설치가 필요합니다.';
END $$;

-- 02. 업무 테이블과 외래 키
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

-- 03. 기존 컬럼 보강 및 데이터 보정
-- 기존 DB에 이미 테이블이 있는 경우 CREATE TABLE IF NOT EXISTS만으로는 신규 컬럼이 추가되지 않는다.
-- 아래 보강 섹션은 과거 패치로 추가된 컬럼/제약을 같은 파일 재실행만으로 반영하기 위한 idempotent migration이다.


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

-- 04. 업무 조회 인덱스
-- 아래 trigram 인덱스는 데이터가 많아졌을 때 ILIKE/유사도 검색 속도를 보완한다.
-- pg_trgm은 앱의 similarity() 검색에도 필요하므로 생성되지 않으면 위 블록에서 스크립트를 중단한다.

CREATE INDEX IF NOT EXISTS idx_issue_case_title_trgm
    ON issue_case USING gin ((lower(title)) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_issue_case_symptom_summary_trgm
    ON issue_case USING gin ((lower(symptom_summary)) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_issue_case_symptom_detail_trgm
    ON issue_case USING gin ((lower(symptom_detail)) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_issue_case_action_detail_trgm
    ON issue_case USING gin ((lower(coalesce(action_detail, ''))) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_issue_case_cause_detail_trgm
    ON issue_case USING gin ((lower(coalesce(cause_detail, ''))) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_issue_case_tags_trgm
    ON issue_case USING gin ((lower(coalesce(tags, ''))) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_knowledge_share_title_trgm
    ON knowledge_share USING gin ((lower(title)) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_knowledge_share_content_trgm
    ON knowledge_share USING gin ((left(lower(content), 1000)) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_knowledge_share_content_full_trgm
    ON knowledge_share USING gin ((lower(content)) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_issue_case_category
    ON issue_case (category);

CREATE INDEX IF NOT EXISTS idx_issue_case_deployment_version
    ON issue_case (deployment_version);

CREATE INDEX IF NOT EXISTS idx_issue_case_completed_date
    ON issue_case (completed_date);

CREATE INDEX IF NOT EXISTS idx_issue_case_created_at
    ON issue_case (created_at);

CREATE INDEX IF NOT EXISTS idx_knowledge_share_created_at
    ON knowledge_share (created_at);

CREATE INDEX IF NOT EXISTS idx_knowledge_share_infra_type_share_id
    ON knowledge_share_infra (infra_type, knowledge_share_id);

CREATE INDEX IF NOT EXISTS idx_knowledge_share_attachment_share_id
    ON knowledge_share_attachment (knowledge_share_id);

CREATE INDEX IF NOT EXISTS idx_work_project_history_created_at
    ON work_project_history (created_at);

CREATE INDEX IF NOT EXISTS idx_work_project_history_upload_id
    ON work_project_history (upload_id);

CREATE INDEX IF NOT EXISTS idx_work_project_history_upload_row_id
    ON work_project_history (upload_id, row_no, id);

CREATE INDEX IF NOT EXISTS idx_work_project_history_client_name_trgm
    ON work_project_history USING gin ((lower(coalesce(client_name, ''))) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_work_project_history_search_trgm
    ON work_project_history USING gin ((lower(rtrim(
        coalesce(client_name || ' ', '') ||
        coalesce(scope || ' ', '') ||
        coalesce(executors || ' ', '') ||
        coalesce(progress_logs || ' ', '') ||
        coalesce(remaining_issues || ' ', '') ||
        coalesce(sales_rep || ' ', '') ||
        coalesce(site_code || ' ', '') ||
        coalesce(project_scale, '')
    ))) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_work_project_history_search_compact_trgm
    ON work_project_history USING gin ((replace(lower(rtrim(
        coalesce(client_name || ' ', '') ||
        coalesce(scope || ' ', '') ||
        coalesce(executors || ' ', '') ||
        coalesce(progress_logs || ' ', '') ||
        coalesce(remaining_issues || ' ', '') ||
        coalesce(sales_rep || ' ', '') ||
        coalesce(site_code || ' ', '') ||
        coalesce(project_scale, '')
    )), ' ', '')) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_work_maintenance_history_created_at
    ON work_maintenance_history (created_at);

CREATE INDEX IF NOT EXISTS idx_work_maintenance_history_upload_id
    ON work_maintenance_history (upload_id);

CREATE INDEX IF NOT EXISTS idx_work_maintenance_history_upload_row_id
    ON work_maintenance_history (upload_id, row_no, id);

CREATE INDEX IF NOT EXISTS idx_work_maintenance_history_name_trgm
    ON work_maintenance_history USING gin ((lower(coalesce(maintenance_name, ''))) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_work_maintenance_history_search_trgm
    ON work_maintenance_history USING gin ((lower(rtrim(
        coalesce(maintenance_name || ' ', '') ||
        coalesce(progress_issues || ' ', '') ||
        coalesce(remarks || ' ', '') ||
        coalesce(main_dev || ' ', '') ||
        coalesce(sub_dev || ' ', '') ||
        coalesce(sales_rep || ' ', '') ||
        coalesce(site_code || ' ', '') ||
        coalesce(region, '')
    ))) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_work_maintenance_history_search_compact_trgm
    ON work_maintenance_history USING gin ((replace(lower(rtrim(
        coalesce(maintenance_name || ' ', '') ||
        coalesce(progress_issues || ' ', '') ||
        coalesce(remarks || ' ', '') ||
        coalesce(main_dev || ' ', '') ||
        coalesce(sub_dev || ' ', '') ||
        coalesce(sales_rep || ' ', '') ||
        coalesce(site_code || ' ', '') ||
        coalesce(region, '')
    )), ' ', '')) gin_trgm_ops);

-- 05. 인프라 값 정규화와 제약 조건
-- 기존 예방점검 인프라 값을 GPM으로 정규화한다. 새 DB에서는 영향이 없다.

ALTER TABLE knowledge_share_infra
    DROP CONSTRAINT IF EXISTS knowledge_share_infra_infra_type_check;


UPDATE issue_case
SET infra_type = 'GPM'
WHERE infra_type IN ('예방점검', '예방 점검');

DELETE FROM knowledge_share_infra legacy
WHERE legacy.infra_type IN ('예방점검', '예방 점검')
  AND EXISTS (
      SELECT 1
      FROM knowledge_share_infra AS normalized_infra
      WHERE normalized_infra.knowledge_share_id = legacy.knowledge_share_id
        AND normalized_infra.infra_type = 'GPM'
  );

UPDATE knowledge_share_infra
SET infra_type = 'GPM'
WHERE infra_type IN ('예방점검', '예방 점검');


ALTER TABLE knowledge_share_infra
    ADD CONSTRAINT knowledge_share_infra_infra_type_check
    CHECK (
        infra_type IN (
            'EMS', 'ERMS', 'SMS', 'NMS', 'GPM', '운영관리',
            'DBMS', 'FMS', 'IMS', 'SYSLOG', 'TRAP', 'TMS',
            'APM', 'BMS', 'STMS', 'RTMS', 'VMS', 'OAM',
            'WNMS', 'CMS', 'K8S', 'TRMS', 'NPM', 'BRMS', '기타'
        )
    );

-- 06. 테이블/컬럼 설명
-- 실행 효과: 테이블/컬럼 설명 메타데이터만 갱신하며 데이터와 컬럼 구조는 변경하지 않는다.


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


-- 07. 검색 테이블/최신 검색 함수/동기화 트리거
-- 검색 인덱스: 원문은 보존하고 단어/글자 추출은 등록·수정 시에만 수행한다.
-- 기존 데이터 검색 인덱스 채우기는 아래 08 단계에서 수행한다.

CREATE OR REPLACE FUNCTION app_search_chars(value text) RETURNS text[]
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    SELECT coalesce(array_agg(DISTINCT ch ORDER BY ch), ARRAY[]::text[])
    FROM regexp_split_to_table(lower(coalesce(value, '')), '') ch
    WHERE ch ~ '[[:alnum:]가-힣]'
$$;

CREATE TABLE IF NOT EXISTS app_search_document (
    source_kind text NOT NULL,
    document_id bigint NOT NULL,
    field_kind text NOT NULL CHECK (field_kind IN ('TITLE', 'CONTENT', 'AUX')),
    normalized_text text NOT NULL,
    compact_text text NOT NULL,
    characters text[] NOT NULL,
    PRIMARY KEY (source_kind, document_id, field_kind)
);
CREATE INDEX IF NOT EXISTS idx_app_search_document_chars ON app_search_document USING gin (characters);

CREATE TABLE IF NOT EXISTS app_search_term (
    word_hash text PRIMARY KEY,
    word text NOT NULL,
    characters text[] NOT NULL,
    word_length integer NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_app_search_term_chars ON app_search_term USING gin (characters);
CREATE INDEX IF NOT EXISTS idx_app_search_term_length ON app_search_term (word_length);

CREATE TABLE IF NOT EXISTS app_search_posting (
    source_kind text NOT NULL,
    document_id bigint NOT NULL,
    field_kind text NOT NULL,
    word_hash text NOT NULL REFERENCES app_search_term(word_hash),
    PRIMARY KEY (source_kind, document_id, field_kind, word_hash),
    FOREIGN KEY (source_kind, document_id, field_kind)
        REFERENCES app_search_document(source_kind, document_id, field_kind) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_app_search_posting_word ON app_search_posting (word_hash, source_kind, field_kind, document_id);

CREATE OR REPLACE FUNCTION app_search_store(source_name text, row_id bigint, title_text text, body_text text, aux_text text)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    field_name text;
    raw_text text;
    normalized text;
    compact text;
    words text[];
    changed integer;
BEGIN
    FOR field_name, raw_text IN
        SELECT * FROM (VALUES ('TITLE', title_text), ('CONTENT', body_text), ('AUX', aux_text)) fields
    LOOP
        normalized := lower(btrim(regexp_replace(coalesce(raw_text, ''), '\s+', ' ', 'g')));
        compact := replace(normalized, ' ', '');
        INSERT INTO app_search_document VALUES
            (source_name, row_id, field_name, normalized, compact, app_search_chars(normalized))
        ON CONFLICT (source_kind, document_id, field_kind) DO UPDATE SET
            normalized_text = excluded.normalized_text,
            compact_text = excluded.compact_text,
            characters = excluded.characters
        WHERE app_search_document.normalized_text IS DISTINCT FROM excluded.normalized_text;
        GET DIAGNOSTICS changed = ROW_COUNT;
        IF changed = 0 THEN CONTINUE; END IF;

        SELECT coalesce(array_agg(DISTINCT word ORDER BY word), ARRAY[]::text[]) INTO words
        FROM (SELECT regexp_split_to_table(normalized, '[^[:alnum:]가-힣]+') word
              UNION ALL SELECT compact) terms WHERE word <> '';
        INSERT INTO app_search_term(word_hash, word, characters, word_length)
        SELECT md5(word), word, app_search_chars(word), char_length(word) FROM unnest(words) word
        ON CONFLICT (word_hash) DO NOTHING;
        -- 해시는 긴 본문/단어로 인한 B-tree 크기 제한을 피하기 위한 키다.
        -- 충돌 시 다른 단어로 연결하지 않고 원문 쓰기와 함께 롤백한다.
        IF EXISTS (SELECT 1 FROM unnest(words) AS w(word) JOIN app_search_term t ON t.word_hash = md5(w.word)
                   WHERE t.word <> w.word) THEN
            RAISE EXCEPTION 'Search term hash collision';
        END IF;
        DELETE FROM app_search_posting WHERE source_kind = source_name AND document_id = row_id AND field_kind = field_name;
        INSERT INTO app_search_posting SELECT source_name, row_id, field_name, md5(word) FROM unnest(words) word;
    END LOOP;
END $$;

CREATE OR REPLACE FUNCTION app_search_sync() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    data jsonb;
    title_text text;
    body_text text;
    aux_text text;
BEGIN
    IF TG_OP = 'DELETE' THEN
        DELETE FROM app_search_document WHERE source_kind = TG_ARGV[0] AND document_id = OLD.id;
        RETURN OLD;
    END IF;
    data := to_jsonb(NEW);
    SELECT string_agg(data ->> name, ' ' ORDER BY ord) INTO title_text
        FROM unnest(string_to_array(TG_ARGV[1], ',')) WITH ORDINALITY fields(name, ord);
    SELECT string_agg(data ->> name, ' ' ORDER BY ord) INTO body_text
        FROM unnest(string_to_array(TG_ARGV[2], ',')) WITH ORDINALITY fields(name, ord);
    SELECT string_agg(data ->> name, ' ' ORDER BY ord) INTO aux_text
        FROM unnest(string_to_array(TG_ARGV[3], ',')) WITH ORDINALITY fields(name, ord);
    PERFORM app_search_store(TG_ARGV[0], NEW.id, title_text, body_text, aux_text);
    RETURN NEW;
END $$;

-- 길이 차이로 불가능한 후보를 먼저 제외하고 인접 문자 교환까지 허용한다.
CREATE OR REPLACE FUNCTION app_search_similarity(word text, query text) RETURNS double precision
LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE AS $$
DECLARE
    n integer := char_length(word);
    m integer := char_length(query);
    threshold double precision := CASE WHEN m <= 3 THEN 0.66 ELSE 0.74 END;
    bonus double precision := CASE WHEN n >= 3 AND m >= 3 AND left(word, 3) = left(query, 3) THEN 0.08 ELSE 0 END;
    prev2 integer[];
    prev integer[];
    curr integer[];
    i integer;
    j integer;
BEGIN
    IF n = 0 OR m = 0 THEN RETURN 0; END IF;
    IF 1.0 - abs(n - m)::double precision / greatest(n, m) + bonus < threshold THEN RETURN 0; END IF;
    prev := ARRAY(SELECT generate_series(0, m));
    prev2 := prev;
    FOR i IN 1..n LOOP
        curr := array_fill(0, ARRAY[m + 1]); curr[1] := i;
        FOR j IN 1..m LOOP
            curr[j + 1] := least(prev[j + 1] + 1, curr[j] + 1,
                prev[j] + CASE WHEN substr(word, i, 1) = substr(query, j, 1) THEN 0 ELSE 1 END);
            IF i > 1 AND j > 1 AND substr(word, i, 1) = substr(query, j - 1, 1)
                AND substr(word, i - 1, 1) = substr(query, j, 1) THEN
                curr[j + 1] := least(curr[j + 1], prev2[j - 1] + 1);
            END IF;
        END LOOP;
        prev2 := prev; prev := curr;
    END LOOP;
    RETURN least(1.0, 1.0 - prev[m + 1]::double precision / greatest(n, m) + bonus);
END $$;

-- 허용 편집 횟수보다 하나 많은 글자 그룹 중 적어도 한 그룹은 전부 일치해야 한다.
-- 접두사 보너스를 받는 단어는 첫 3글자 그룹으로 별도 포함한다.
CREATE OR REPLACE FUNCTION app_search_char_groups(query_text text)
RETURNS TABLE(required_chars text[]) LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    WITH settings AS (
        SELECT char_length(query_text) AS m,
               CASE WHEN char_length(query_text) <= 3 THEN 0.66 ELSE 0.74 END AS threshold
    ), letters AS (
        SELECT DISTINCT ch FROM regexp_split_to_table(query_text, '') ch
        WHERE ch ~ '[[:alnum:]가-힣]'
    ), numbered AS (
        SELECT ch, row_number() OVER (ORDER BY ch) AS rn, count(*) OVER () AS n FROM letters
    ), groups AS (
        SELECT array_agg(ch ORDER BY ch) AS required_chars
        FROM numbered CROSS JOIN settings
        GROUP BY (rn - 1) % least(n, floor((1 - threshold) * greatest(m, floor(m / threshold)))::bigint + 1)
        UNION
        SELECT app_search_chars(left(query_text, 3)) WHERE char_length(query_text) >= 3
    )
    SELECT required_chars FROM groups
$$;
CREATE OR REPLACE FUNCTION app_search_candidates_scope(source_name text, requested_field text, keyword text, allowed_ids bigint[])
RETURNS TABLE(document_id bigint, score integer) LANGUAGE sql STABLE AS $$
    WITH q AS MATERIALIZED (
        SELECT lower(btrim(regexp_replace(keyword, '\s+', ' ', 'g'))) AS normalized_text,
               regexp_replace(lower(keyword), '\s+', '', 'g') AS compact,
               app_search_chars(keyword) AS characters,
               regexp_split_to_array(regexp_replace(lower(keyword), '\s+', '', 'g'), '') AS letters
        WHERE keyword IS NOT NULL AND regexp_replace(keyword, '\s+', '', 'g') <> ''
    ), documents AS MATERIALIZED (
        -- 본문 문자열은 한 번만 읽고 이후에는 ID, 글자 배열, 점수만 사용한다.
        SELECT d.document_id, d.field_kind, d.characters, pg_column_size(d.normalized_text) AS stored_bytes,
               CASE WHEN strpos(d.normalized_text, q.normalized_text) > 0 THEN 600000
                    WHEN strpos(d.compact_text, q.compact) > 0 THEN 500000 ELSE 0 END AS exact_score
        FROM q JOIN app_search_document d
          ON d.characters && q.characters OR cardinality(q.characters) = 0
        WHERE (allowed_ids IS NULL OR d.document_id = ANY(allowed_ids))
          AND d.source_kind = source_name
          AND (requested_field = 'ANY' OR d.field_kind = requested_field)
    ), strategy AS MATERIALIZED (
        SELECT requested_field = 'TITLE' OR coalesce(sum(stored_bytes), 0) <= 65536 AS scope_first
        FROM documents
    ), title_postings AS MATERIALIZED (
        -- 제목은 짧으므로 해당 제목의 단어 연결만 읽는다.
        -- 공용 사전에서 agent 후보 18만 개를 먼저 고르는 비용을 피한다.
        SELECT p.document_id, p.field_kind, p.word_hash
        FROM documents d JOIN app_search_posting p
          ON p.source_kind = source_name AND p.document_id = d.document_id AND p.field_kind = d.field_kind
        WHERE (SELECT scope_first FROM strategy) AND d.exact_score = 0
    ), title_terms AS MATERIALIZED (
        SELECT t.word_hash, t.word
        FROM (SELECT DISTINCT word_hash FROM title_postings) c
        JOIN app_search_term t USING (word_hash)
        CROSS JOIN q
        WHERE t.characters && q.characters
          AND t.word_length BETWEEN ceil(char_length(q.compact) * CASE WHEN char_length(q.compact) <= 3 THEN 0.58 ELSE 0.66 END)::integer
                                AND floor(char_length(q.compact) / CASE WHEN char_length(q.compact) <= 3 THEN 0.58 ELSE 0.66 END)::integer
          -- 편집 거리로도 임계값에 도달할 수 없는 글자 조합은 먼저 제외한다.
          -- 검색어의 중복 글자도 세며, 접두사 보너스는 넉넉하게 허용해 결과 누락을 막는다.
          AND (SELECT count(*) FROM unnest(q.letters) ch
               WHERE ch ~ '[[:alnum:]가-힣]' AND NOT (ch = ANY(t.characters)))
              <= floor((1.0 -
                  CASE WHEN char_length(q.compact) <= 3 THEN 0.66 ELSE 0.74 END +
                  CASE WHEN char_length(q.compact) >= 3 AND t.word_length >= 3 THEN 0.08 ELSE 0 END)
                  * greatest(char_length(q.compact), t.word_length))
    ), filtered_terms AS MATERIALIZED (
        -- 길이/글자 인덱스로 먼저 줄인다. 문서의 모든 단어 해시를 모으지 않는다.
        -- 길이 컬럼을 numeric으로 변환하지 않아 정수 B-tree 인덱스 조건을 유지한다.
        SELECT DISTINCT t.word_hash, t.word
        FROM q CROSS JOIN LATERAL app_search_char_groups(q.compact) g
        JOIN app_search_term t ON t.characters @> g.required_chars
        WHERE NOT (SELECT scope_first FROM strategy) AND t.characters && q.characters
          AND t.word_length BETWEEN ceil(char_length(q.compact) * CASE WHEN char_length(q.compact) <= 3 THEN 0.58 ELSE 0.66 END)::integer
                                AND floor(char_length(q.compact) / CASE WHEN char_length(q.compact) <= 3 THEN 0.58 ELSE 0.66 END)::integer
          -- 편집 거리로도 임계값에 도달할 수 없는 글자 조합은 먼저 제외한다.
          -- 검색어의 중복 글자도 세며, 접두사 보너스는 넉넉하게 허용해 결과 누락을 막는다.
          AND (SELECT count(*) FROM unnest(q.letters) ch
               WHERE ch ~ '[[:alnum:]가-힣]' AND NOT (ch = ANY(t.characters)))
              <= floor((1.0 -
                  CASE WHEN char_length(q.compact) <= 3 THEN 0.66 ELSE 0.74 END +
                  CASE WHEN char_length(q.compact) >= 3 AND t.word_length >= 3 THEN 0.08 ELSE 0 END)
                  * greatest(char_length(q.compact), t.word_length))
    ), posting_strategy AS MATERIALIZED (
        SELECT count(*) > 4096 AS source_first FROM filtered_terms
    ), source_postings AS MATERIALIZED (
        SELECT p.document_id, p.field_kind, p.word_hash
        FROM documents d JOIN app_search_posting p
          ON p.source_kind = source_name AND p.document_id = d.document_id AND p.field_kind = d.field_kind
        WHERE d.exact_score = 0 AND (SELECT source_first FROM posting_strategy)
    ), filtered_postings AS MATERIALIZED (
        -- 길이/글자 조건을 통과한 단어의 연결만 먼저 조회한다.
        -- 문서 수 × 단어 수만큼 기본키를 반복 조회하는 실행 계획을 방지한다.
        SELECT p.document_id, p.field_kind, p.word_hash
        FROM filtered_terms t JOIN app_search_posting p ON p.word_hash = t.word_hash
        WHERE NOT (SELECT source_first FROM posting_strategy) AND p.source_kind = source_name
          AND (requested_field = 'ANY' OR p.field_kind = requested_field)
        UNION ALL
        SELECT document_id, field_kind, word_hash FROM title_postings
        UNION ALL
        SELECT p.document_id, p.field_kind, p.word_hash
        FROM source_postings p JOIN filtered_terms t ON t.word_hash = p.word_hash
    ), relevant_terms AS MATERIALIZED (
        SELECT DISTINCT t.word_hash, t.word
        FROM filtered_terms t JOIN filtered_postings p ON p.word_hash = t.word_hash
        JOIN documents d ON d.document_id = p.document_id AND d.field_kind = p.field_kind
        WHERE NOT (SELECT scope_first FROM strategy) AND d.exact_score = 0
        UNION ALL
        SELECT word_hash, word FROM title_terms
    ), term_scores AS MATERIALIZED (
        SELECT t.word_hash, app_search_similarity(t.word, q.compact) AS similarity
        FROM relevant_terms t CROSS JOIN q
    ), typo_hits AS MATERIALIZED (
        SELECT p.document_id, p.field_kind, max(t.similarity) AS similarity
        FROM term_scores t CROSS JOIN q JOIN filtered_postings p ON p.word_hash = t.word_hash
        JOIN documents d ON d.document_id = p.document_id AND d.field_kind = p.field_kind AND d.exact_score = 0
        WHERE t.similarity >= CASE WHEN char_length(q.compact) <= 3 THEN 0.66 ELSE 0.74 END
        GROUP BY p.document_id, p.field_kind
    ), hits AS (
        SELECT d.document_id,
            CASE
                WHEN d.exact_score > 0 THEN
                    d.exact_score + CASE d.field_kind WHEN 'TITLE' THEN 2000 WHEN 'CONTENT' THEN 1000 ELSE 0 END
                WHEN t.similarity IS NOT NULL THEN
                    400000 + CASE d.field_kind WHEN 'TITLE' THEN 2000 WHEN 'CONTENT' THEN 1000 ELSE 0 END + floor(t.similarity * 1000)::integer
                WHEN overlap.n >= CASE WHEN char_length(q.compact) >= 5 THEN 3 WHEN char_length(q.compact) >= 3 THEN 2 ELSE 2147483647 END
                    THEN 1000 + least(overlap.n, 20) + CASE WHEN d.field_kind = 'TITLE' THEN 100 ELSE 0 END
                ELSE 0
            END AS score
        FROM documents d CROSS JOIN q
        LEFT JOIN typo_hits t ON t.document_id = d.document_id AND t.field_kind = d.field_kind
        CROSS JOIN LATERAL (SELECT count(*)::integer AS n FROM unnest(q.characters) ch WHERE ch = ANY(d.characters)) overlap
    ), combined_hits AS (
        SELECT d.document_id, 1000 + least(count(DISTINCT ch)::integer, 20) AS score
        FROM documents d CROSS JOIN q
        CROSS JOIN LATERAL unnest(q.characters) ch
        WHERE requested_field = 'ANY' AND ch = ANY(d.characters)
        GROUP BY d.document_id, q.characters, q.compact
        HAVING count(DISTINCT ch) >= CASE WHEN char_length(q.compact) >= 5 THEN 3 WHEN char_length(q.compact) >= 3 THEN 2 ELSE 2147483647 END
    )
    SELECT all_hits.document_id, max(all_hits.score)::integer
    FROM (SELECT * FROM hits WHERE hits.score > 0 UNION ALL SELECT * FROM combined_hits) all_hits
    GROUP BY all_hits.document_id
$$;
-- 기존 단일 검색/상세 검색 호출도 같은 엔진으로 처리한다.
CREATE OR REPLACE FUNCTION app_search_candidates(source_name text, requested_field text, keyword text)
RETURNS TABLE(document_id bigint, score integer) LANGUAGE sql STABLE AS $$
    SELECT * FROM app_search_candidates_scope(source_name, requested_field, keyword, NULL::bigint[])
$$;

-- 필터를 통과한 글만 검색한다. 제목+내용은 제목 후보 안에서 내용을 평가한다.
CREATE OR REPLACE FUNCTION app_search_rank(source_name text, keyword text, title_keyword text,
                                          content_keyword text, allowed_ids bigint[])
RETURNS TABLE(document_id bigint, score integer, tie_score integer)
LANGUAGE plpgsql STABLE AS $$
DECLARE
    title_ids bigint[];
    title_scores integer[];
    has_title boolean := title_keyword IS NOT NULL AND btrim(title_keyword) <> '';
    has_content boolean := content_keyword IS NOT NULL AND btrim(content_keyword) <> '';
BEGIN
    IF cardinality(allowed_ids) = 0 THEN RETURN; END IF;
    IF has_title AND has_content THEN
        SELECT coalesce(array_agg(t.document_id ORDER BY t.document_id), ARRAY[]::bigint[]), array_agg(t.score ORDER BY t.document_id)
        INTO title_ids, title_scores
        FROM app_search_candidates_scope(source_name, 'TITLE', title_keyword, allowed_ids) t;
        IF cardinality(title_ids) = 0 THEN RETURN; END IF;
        RETURN QUERY
        SELECT t.id, greatest(t.points, c.score), t.points + c.score
        FROM unnest(title_ids, title_scores) t(id, points)
        JOIN app_search_candidates_scope(source_name, 'CONTENT', content_keyword, title_ids) c
          ON c.document_id = t.id;
    ELSIF has_title OR has_content THEN
        RETURN QUERY SELECT c.document_id, c.score, c.score
        FROM app_search_candidates_scope(source_name,
            CASE WHEN has_title THEN 'TITLE' ELSE 'CONTENT' END,
            CASE WHEN has_title THEN title_keyword ELSE content_keyword END, allowed_ids) c;
    ELSIF keyword IS NOT NULL AND btrim(keyword) <> '' THEN
        RETURN QUERY SELECT c.document_id, c.score, c.score
        FROM app_search_candidates_scope(source_name, 'ANY', keyword, allowed_ids) c;
    ELSE
        RETURN QUERY SELECT id, 0, 0 FROM unnest(allowed_ids) id;
    END IF;
END $$;

DROP TRIGGER IF EXISTS app_search_sync ON issue_case;
CREATE TRIGGER app_search_sync AFTER INSERT OR UPDATE OF title, symptom_summary, symptom_detail, cause_detail, action_detail, tags, system_name, customer_name, category, deployment_version, version_info OR DELETE ON issue_case
FOR EACH ROW EXECUTE FUNCTION app_search_sync('PATCH', 'title', 'symptom_summary,symptom_detail,action_detail,cause_detail', 'tags,system_name,customer_name,category,deployment_version,version_info');
DROP TRIGGER IF EXISTS app_search_sync ON knowledge_share;
CREATE TRIGGER app_search_sync AFTER INSERT OR UPDATE OF title, content, customer_name, author_name, attachment_name OR DELETE ON knowledge_share
FOR EACH ROW EXECUTE FUNCTION app_search_sync('KNOWLEDGE', 'title', 'content', 'customer_name,author_name,attachment_name');
DROP TRIGGER IF EXISTS app_search_sync ON work_project_history;
CREATE TRIGGER app_search_sync AFTER INSERT OR UPDATE OF client_name, scope, progress_logs, remaining_issues, executors, sales_rep, site_code, project_scale OR DELETE ON work_project_history
FOR EACH ROW EXECUTE FUNCTION app_search_sync('PROJECT', 'client_name', 'scope,progress_logs,remaining_issues', 'executors,sales_rep,site_code,project_scale');
DROP TRIGGER IF EXISTS app_search_sync ON work_maintenance_history;
CREATE TRIGGER app_search_sync AFTER INSERT OR UPDATE OF maintenance_name, progress_issues, remarks, main_dev, sub_dev, sales_rep, site_code, region OR DELETE ON work_maintenance_history
FOR EACH ROW EXECUTE FUNCTION app_search_sync('MAINTENANCE', 'maintenance_name', 'progress_issues,remarks', 'main_dev,sub_dev,sales_rep,site_code,region');

COMMIT;

-- 08. 기존 원문의 검색 데이터 반영 (자동 커밋 상태, 배치당 500행)
CREATE OR REPLACE PROCEDURE app_search_backfill(batch_size integer DEFAULT 500)
LANGUAGE plpgsql AS $$
DECLARE
    cfg record;
    last_id bigint;
    batch_last bigint;
    batch_count integer;
    total_count bigint;
    changed_count integer;
BEGIN
    IF batch_size IS NULL OR batch_size < 1 THEN
        RAISE EXCEPTION 'batch_size must be positive';
    END IF;
    CREATE TEMP TABLE backfill_input (
        id bigint, title_text text, body_text text, aux_text text
    ) ON COMMIT DELETE ROWS;
    CREATE TEMP TABLE backfill_fields (
        document_id bigint, field_kind text, normalized_text text, compact_text text,
        PRIMARY KEY(document_id, field_kind)
    ) ON COMMIT DELETE ROWS;
    CREATE TEMP TABLE backfill_words (
        document_id bigint, field_kind text, word text, word_hash text
    ) ON COMMIT DELETE ROWS;
    CREATE TEMP TABLE backfill_terms (
        word_hash text PRIMARY KEY, word text
    ) ON COMMIT DELETE ROWS;

    FOR cfg IN SELECT * FROM (VALUES
        ('PATCH', 'issue_case', 'title',
         'concat_ws('' '', symptom_summary, symptom_detail, action_detail, cause_detail)',
         'concat_ws('' '', tags, system_name, customer_name, category, deployment_version, version_info)'),
        ('KNOWLEDGE', 'knowledge_share', 'title', 'content',
         'concat_ws('' '', customer_name, author_name, attachment_name)'),
        ('PROJECT', 'work_project_history', 'client_name',
         'concat_ws('' '', scope, progress_logs, remaining_issues)',
         'concat_ws('' '', executors, sales_rep, site_code, project_scale)'),
        ('MAINTENANCE', 'work_maintenance_history', 'maintenance_name',
         'concat_ws('' '', progress_issues, remarks)',
         'concat_ws('' '', main_dev, sub_dev, sales_rep, site_code, region)')
    ) AS sources(kind, table_name, title_expr, body_expr, aux_expr)
    LOOP
        last_id := NULL;
        total_count := 0;
        RAISE NOTICE '% 시작 (배치 %행)', cfg.table_name, batch_size;
        LOOP
            EXECUTE format(
                'INSERT INTO backfill_input SELECT id, %s, %s, %s FROM %I
                 WHERE ($1 IS NULL OR id > $1) ORDER BY id LIMIT $2',
                cfg.title_expr, cfg.body_expr, cfg.aux_expr, cfg.table_name)
                USING last_id, batch_size;
            SELECT count(*), max(id) INTO batch_count, batch_last FROM backfill_input;
            EXIT WHEN batch_count = 0;

            -- 변경 없는 문서는 글자/단어 추출 전에 제외한다.
            INSERT INTO backfill_fields
            SELECT i.id, f.field_kind, n.value, replace(n.value, ' ', '')
            FROM backfill_input i
            CROSS JOIN LATERAL (VALUES ('TITLE', i.title_text),
                ('CONTENT', i.body_text), ('AUX', i.aux_text)) f(field_kind, raw_text)
            CROSS JOIN LATERAL (
                SELECT lower(btrim(regexp_replace(coalesce(f.raw_text, ''), '\s+', ' ', 'g'))) AS value
            ) n
            LEFT JOIN app_search_document d ON d.source_kind = cfg.kind
                AND d.document_id = i.id AND d.field_kind = f.field_kind
            WHERE d.document_id IS NULL OR d.normalized_text IS DISTINCT FROM n.value;
            GET DIAGNOSTICS changed_count = ROW_COUNT;

            INSERT INTO app_search_document
            SELECT cfg.kind, document_id, field_kind, normalized_text, compact_text,
                   app_search_chars(normalized_text)
            FROM backfill_fields
            ON CONFLICT (source_kind, document_id, field_kind) DO UPDATE SET
                normalized_text = excluded.normalized_text,
                compact_text = excluded.compact_text, characters = excluded.characters;

            INSERT INTO backfill_words
            SELECT DISTINCT f.document_id, f.field_kind, w.word, md5(w.word)
            FROM backfill_fields f CROSS JOIN LATERAL (
                SELECT regexp_split_to_table(f.normalized_text, '[^[:alnum:]가-힣]+') AS word
                UNION SELECT f.compact_text
            ) w WHERE w.word <> '';

            IF EXISTS (SELECT 1 FROM backfill_words GROUP BY word_hash HAVING count(DISTINCT word) > 1) THEN
                RAISE EXCEPTION 'Search term hash collision';
            END IF;
            INSERT INTO backfill_terms SELECT DISTINCT word_hash, word FROM backfill_words;
            -- 같은 단어는 배치 안에서 한 번만, 이미 등록된 단어는 추출 없이 재사용한다.
            INSERT INTO app_search_term(word_hash, word, characters, word_length)
            SELECT t.word_hash, t.word, app_search_chars(t.word), char_length(t.word)
            FROM backfill_terms t
            WHERE NOT EXISTS (SELECT 1 FROM app_search_term a WHERE a.word_hash = t.word_hash)
            ON CONFLICT (word_hash) DO NOTHING;
            IF EXISTS (SELECT 1 FROM backfill_terms t JOIN app_search_term a USING(word_hash)
                       WHERE t.word <> a.word) THEN
                RAISE EXCEPTION 'Search term hash collision';
            END IF;
            DELETE FROM app_search_posting p USING backfill_fields f
            WHERE p.source_kind = cfg.kind AND p.document_id = f.document_id AND p.field_kind = f.field_kind;
            INSERT INTO app_search_posting
            SELECT cfg.kind, document_id, field_kind, word_hash FROM backfill_words;

            last_id := batch_last;
            total_count := total_count + batch_count;
            COMMIT;
            RAISE NOTICE '%: 누적 %행 확인, 마지막 ID %, 이번 배치 변경 문서 %개 커밋 완료',
                cfg.table_name, total_count, last_id, changed_count;
        END LOOP;
        RAISE NOTICE '% 완료: %행', cfg.table_name, total_count;
    END LOOP;
    DROP TABLE backfill_input, backfill_fields, backfill_words, backfill_terms;
END $$;

CALL app_search_backfill(500);
DROP PROCEDURE app_search_backfill(integer);
-- 09. 통계 갱신 및 완료 확인
ANALYZE account;
ANALYZE issue_case;
ANALYZE knowledge_share;
ANALYZE knowledge_share_infra;
ANALYZE knowledge_share_attachment;
ANALYZE patch_history_upload_log;
ANALYZE work_report_upload;
ANALYZE work_project_history;
ANALYZE work_maintenance_history;
ANALYZE app_search_document;
ANALYZE app_search_term;
ANALYZE app_search_posting;

DO $$
DECLARE
    missing_objects text;
    trigger_count integer;
BEGIN
    SELECT string_agg(name, ', ' ORDER BY name) INTO missing_objects
    FROM unnest(ARRAY['account', 'issue_case', 'knowledge_share', 'knowledge_share_infra',
        'knowledge_share_attachment', 'patch_history_upload_log', 'work_report_upload',
        'work_project_history', 'work_maintenance_history', 'app_search_document',
        'app_search_term', 'app_search_posting']) AS tables(name)
    WHERE to_regclass(format('%I.%I', current_schema(), name)) IS NULL;
    IF missing_objects IS NOT NULL THEN
        RAISE EXCEPTION '테이블 생성 누락: %', missing_objects;
    END IF;

    SELECT string_agg(signature, ', ' ORDER BY signature) INTO missing_objects
    FROM unnest(ARRAY['app_search_chars(text)', 'app_search_store(text,bigint,text,text,text)',
        'app_search_sync()', 'app_search_similarity(text,text)', 'app_search_char_groups(text)',
        'app_search_candidates_scope(text,text,text,bigint[])', 'app_search_candidates(text,text,text)',
        'app_search_rank(text,text,text,text,bigint[])']) AS functions(signature)
    WHERE to_regprocedure(format('%I.%s', current_schema(), signature)) IS NULL;
    IF missing_objects IS NOT NULL THEN
        RAISE EXCEPTION '검색 함수 생성 누락: %', missing_objects;
    END IF;

    SELECT count(*) INTO trigger_count FROM pg_trigger
    WHERE tgname = 'app_search_sync' AND NOT tgisinternal AND tgenabled <> 'D'
      AND tgrelid IN ('issue_case'::regclass, 'knowledge_share'::regclass,
          'work_project_history'::regclass, 'work_maintenance_history'::regclass);
    IF trigger_count <> 4 THEN
        RAISE EXCEPTION '검색 동기화 트리거 생성 누락: % / 4', trigger_count;
    END IF;
    RAISE NOTICE '초기 구축 완료: 테이블 12개, 검색 함수 8개, 검색 동기화 트리거 4개 확인';
END $$;

SELECT current_database() AS database_name, current_user AS owner_name,
       current_schema() AS schema_name;
SELECT source_kind, count(DISTINCT document_id) AS document_count,
       count(*) AS field_count
FROM app_search_document GROUP BY source_kind ORDER BY source_kind;