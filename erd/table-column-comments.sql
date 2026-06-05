-- PostgreSQL table/column comments for the current issue_system project.
-- 기준: Spring JPA Entity, ElementCollection, JdbcTemplate Repository 사용 테이블.
-- 실행 효과: 테이블/컬럼 설명 메타데이터만 갱신하며 데이터와 컬럼 구조는 변경하지 않는다.

BEGIN;

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
COMMENT ON COLUMN issue_case.category IS '업무 구분 또는 분류값';
COMMENT ON COLUMN issue_case.deployment_version IS '배포 버전';
COMMENT ON COLUMN issue_case.created_at IS '최초 생성 일시';
COMMENT ON COLUMN issue_case.updated_at IS '마지막 수정 일시';

COMMENT ON TABLE knowledge_share IS '지식공유 게시글 저장 테이블';
COMMENT ON COLUMN knowledge_share.id IS '지식공유 게시글 고유 ID';
COMMENT ON COLUMN knowledge_share.title IS '지식공유 제목';
COMMENT ON COLUMN knowledge_share.customer_name IS '고객사명';
COMMENT ON COLUMN knowledge_share.author_name IS '담당자 또는 작성자명';
COMMENT ON COLUMN knowledge_share.attachment_name IS '기존 화면 호환용 첨부파일명';
COMMENT ON COLUMN knowledge_share.content IS '지식공유 본문 내용';
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
