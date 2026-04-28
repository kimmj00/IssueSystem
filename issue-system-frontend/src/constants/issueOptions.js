// 백엔드 API 기본 주소
// 개발 환경에서는 Spring Boot 서버 주소를 직접 바라보고,
// 배포 환경에서는 같은 도메인 기준으로 호출합니다.
export const API_BASE =
  process.env.NODE_ENV === 'development'
    ? 'http://localhost:8080'
    : '';

// 인프라 옵션
// 백엔드 InfraType enum 값과 동일해야 검색/등록이 정상 동작합니다.
export const infraOptions = [
  'EMS', '예방점검', 'ERMS', 'SMS', 'NMS', 'DBMS', 'FMS', 'IMS',
  'SYSLOG', 'TRAP', 'TMS', 'APM', 'BMS', 'STMS', 'RTMS', 'VMS',
  'OAM', 'WNMS', 'CMS', 'K8S', 'TRMS', 'NPM', 'BRMS'
];

// 구분 옵션
// 직접입력도 가능하게 모달에서 별도로 처리합니다.
export const categoryOptions = ['Tomcat', 'JAVA', 'WEB', 'DB', 'Agent', 'Manager', '보안취약'];

// 상태 옵션
// 백엔드 IssueStatus enum 값과 동일해야 합니다.
export const statusOptions = ['OPEN', 'RESOLVED', 'CLOSED'];

// 등록 폼 초기값
// 백엔드 IssueCaseCreateRequest에서 title, infraType, systemName, status,
// symptomSummary, symptomDetail, authorName은 필수입니다.
export const emptyForm = {
  title: '',
  infraType: 'EMS',
  systemName: '',
  customerName: '',
  category: '',
  versionInfo: '',
  deploymentVersion: '',
  status: 'RESOLVED',
  symptomSummary: '',
  symptomDetail: '',
  causeDetail: '',
  actionDetail: '',
  tags: '',
  authorName: '',
};
