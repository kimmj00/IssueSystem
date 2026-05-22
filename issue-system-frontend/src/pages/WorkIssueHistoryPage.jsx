import React, { useMemo, useState } from 'react';
import SectionCard from '../components/common/SectionCard';

// 작업 및 이슈이력 화면은 기존 상단 메뉴/페이지 껍데기는 유지하고,
// 이 파일 안쪽 내용 영역만 프로젝트/유지보수 현황 UI로 채웁니다.
const INNER_TABS = [
  { key: 'projects', label: '프로젝트 현황' },
  { key: 'maintenance', label: '유지보수 현황' },
  { key: 'search', label: '통합검색' },
];

// 현재는 백엔드 API 연결 전 단계라 화면 확인용 임시 데이터만 둡니다.
// 실제 DB 연동 시 이 배열을 API 조회 결과로 교체하면 됩니다.
const PROJECT_ROWS = [
  {
    id: 'project-2',
    no: '2',
    customerName: '법무부',
    siteCode: 'A23124',
    projectType: '단순구축',
    executors: ['최승훈', '전우진'],
    startDate: '25/7/9',
    infraTypes: ['SMS', 'NMS'],
    latestIssue: '담당자 전부 바뀜',
    detail: [
      'PM 리스트 및 작업 전 준비사항 전달했으나 준비 미흡',
      '변경된 담당자 미팅 진행',
      '현재 상황 및 7.0, 8.0 기준 설명',
      '필요정보 요청',
    ],
  },
  {
    id: 'project-3',
    no: '3',
    customerName: 'HCT',
    siteCode: 'C19203',
    projectType: '단순구축',
    executors: ['최승훈', '고혁배'],
    startDate: '25/10/30',
    infraTypes: ['SMS', 'NMS'],
    latestIssue: 'NMS 등록 대상 준비상태 확인 요청 > 김영민 부장님',
    detail: [
      '에이전트 설치, 삭제, 재설치 매뉴얼 작성 완료',
      '스위치 장비 입고 이후 추가 진행 예정',
    ],
  },
];

const MAINTENANCE_ROWS = [
  {
    id: 'maintenance-9',
    no: '9',
    customerName: '전문건설공제조합',
    siteCode: 'C26020',
    projectType: 'EMS구축',
    executors: ['최승훈', '전우진'],
    contractEnd: '2027/12/31',
    infraTypes: ['SMS', 'NMS', 'DBMS'],
    latestIssue: '포트 감지설정 등록 완료',
    inspection: '2월 2/26, 4월 4/8',
    detail: [
      '4/28 방문 작업 진행',
      'TCP 상태 확인 요청',
      '대시보드 사용 여부 확인 필요',
    ],
  },
  {
    id: 'maintenance-11',
    no: '11',
    customerName: '에너지기술평가원',
    siteCode: 'C26195',
    projectType: '정기 유지보수',
    executors: ['최승훈', '오보성'],
    contractEnd: '2026/12/31',
    infraTypes: ['SMS', 'NMS', 'APM'],
    latestIssue: '에이전트 재설치 안됨 현상 완료',
    inspection: '3월 3/17',
    detail: [
      '4/7 방문 작업 진행',
      '대시보드 대상 추가 및 세션 설정',
      '대시보드 설정 변경 완료',
    ],
  },
];

const INFRA_TYPES = ['SMS', 'NMS', 'DBMS', 'APM'];

function SearchIcon() {
  return (
    <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <circle cx="11" cy="11" r="7" />
      <path d="M20 20l-3.5-3.5" />
    </svg>
  );
}

function UploadIcon() {
  return (
    <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <path d="M14 2v6h6" />
      <path d="M12 18v-6" />
      <path d="M9 15l3-3 3 3" />
    </svg>
  );
}

function FilterIcon() {
  return (
    <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M22 3H2l8 9.5V19l4 2v-8.5L22 3z" />
    </svg>
  );
}

function ChevronIcon({ open }) {
  return (
    <svg
      className={`h-4 w-4 transition ${open ? 'rotate-90' : ''}`}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
    >
      <path d="M9 18l6-6-6-6" />
    </svg>
  );
}

// 공백 기준으로 입력한 검색어가 모두 포함될 때만 통과시키는 AND 검색입니다.
function matchAllKeywords(row, keyword) {
  const words = keyword
    .trim()
    .toLowerCase()
    .split(/\s+/)
    .filter(Boolean);

  if (words.length === 0) return true;

  const target = [
    row.no,
    row.customerName,
    row.siteCode,
    row.projectType,
    row.executors.join(' '),
    row.startDate,
    row.contractEnd,
    row.latestIssue,
    row.inspection,
    row.infraTypes.join(' '),
    row.detail.join(' '),
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();

  return words.every((word) => target.includes(word));
}

function getFilteredRows(rows, { keyword, executor, customer, infraTypes }) {
  return rows.filter((row) => {
    const keywordMatched = matchAllKeywords(row, keyword);
    const executorMatched = !executor || row.executors.includes(executor);
    const customerMatched = !customer || row.customerName === customer;
    const infraMatched = infraTypes.length === 0 || infraTypes.every((infra) => row.infraTypes.includes(infra));

    return keywordMatched && executorMatched && customerMatched && infraMatched;
  });
}

function WorkIssueFilterBar({
  keyword,
  setKeyword,
  executor,
  setExecutor,
  customer,
  setCustomer,
  selectedInfraTypes,
  setSelectedInfraTypes,
  executorOptions,
  customerOptions,
  showCustomerFilter,
  onReset,
}) {
  const toggleInfra = (infra) => {
    setSelectedInfraTypes((prev) =>
      prev.includes(infra) ? prev.filter((item) => item !== infra) : [...prev, infra]
    );
  };

  return (
    <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
      <div className="flex flex-wrap items-end gap-4">
        <div className="min-w-[220px] flex-1">
          <label className="mb-1.5 block text-xs font-semibold text-slate-500">검색어 (AND 조건)</label>
          <div className="relative">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">
              <SearchIcon />
            </span>
            <input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="검색어 입력..."
              className="h-10 w-full rounded-lg border border-slate-300 bg-white pl-9 pr-3 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            />
          </div>
        </div>

        <div className="w-[180px]">
          <label className="mb-1.5 block text-xs font-semibold text-slate-500">수행인원</label>
          <select
            value={executor}
            onChange={(event) => setExecutor(event.target.value)}
            className="h-10 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
          >
            <option value="">전체 수행인원</option>
            {executorOptions.map((name) => (
              <option key={name} value={name}>{name}</option>
            ))}
          </select>
        </div>

        {showCustomerFilter ? (
          <div className="w-[180px]">
            <label className="mb-1.5 block text-xs font-semibold text-slate-500">고객사</label>
            <select
              value={customer}
              onChange={(event) => setCustomer(event.target.value)}
              className="h-10 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            >
              <option value="">전체 고객사</option>
              {customerOptions.map((name) => (
                <option key={name} value={name}>{name}</option>
              ))}
            </select>
          </div>
        ) : null}

        <div>
          <label className="mb-1.5 block text-xs font-semibold text-slate-500">인프라 필터</label>
          <div className="flex flex-wrap gap-2">
            {INFRA_TYPES.map((infra) => {
              const selected = selectedInfraTypes.includes(infra);

              return (
                <button
                  key={infra}
                  type="button"
                  onClick={() => toggleInfra(infra)}
                  className={`rounded-lg border px-3 py-2 text-xs font-bold transition ${
                    selected
                      ? 'border-blue-600 bg-blue-600 text-white'
                      : 'border-slate-300 bg-white text-slate-500 hover:border-blue-400 hover:text-blue-600'
                  }`}
                >
                  {infra}
                </button>
              );
            })}
          </div>
        </div>

        <button
          type="button"
          onClick={onReset}
          className="ml-auto inline-flex h-10 items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 text-sm font-medium text-slate-600 transition hover:bg-slate-100"
        >
          <FilterIcon />
          Reset
        </button>
      </div>
    </div>
  );
}

function ProjectTable({ rows }) {
  const [openedId, setOpenedId] = useState('');

  return (
    <div className="overflow-hidden rounded-xl border border-slate-200">
      <table className="w-full border-collapse text-left text-sm">
        <thead className="bg-slate-100 text-xs font-bold uppercase tracking-wide text-slate-500">
          <tr>
            <th className="w-20 px-4 py-3">NO</th>
            <th className="px-4 py-3">고객사(사업명)</th>
            <th className="px-4 py-3">수행인원</th>
            <th className="px-4 py-3">시작일</th>
            <th className="px-4 py-3">금주 진행사항 (최신)</th>
            <th className="w-14 px-4 py-3" />
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-200 bg-white">
          {rows.map((row) => {
            const open = openedId === row.id;

            return (
              <React.Fragment key={row.id}>
                <tr
                  className="cursor-pointer transition hover:bg-slate-50"
                  onClick={() => setOpenedId(open ? '' : row.id)}
                >
                  <td className="px-4 py-4 font-mono text-slate-500">{row.no}</td>
                  <td className="px-4 py-4">
                    <div className="font-bold text-slate-900">{row.customerName}({row.siteCode})</div>
                    <div className="mt-0.5 text-xs text-slate-500">{row.projectType}</div>
                  </td>
                  <td className="px-4 py-4 text-slate-700">{row.executors.join(', ')}</td>
                  <td className="px-4 py-4 font-mono text-slate-600">{row.startDate}</td>
                  <td className="px-4 py-4 text-slate-700">{row.latestIssue}</td>
                  <td className="px-4 py-4 text-slate-400">
                    <ChevronIcon open={open} />
                  </td>
                </tr>

                {open ? (
                  <tr className="bg-slate-50">
                    <td colSpan={6} className="px-4 py-4">
                      <div className="rounded-xl border border-slate-200 bg-white p-4">
                        <div className="mb-2 text-xs font-bold text-slate-500">상세 진행 내용</div>
                        <ul className="space-y-1 text-sm text-slate-700">
                          {row.detail.map((item) => (
                            <li key={item} className="flex gap-2">
                              <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-blue-500" />
                              <span>{item}</span>
                            </li>
                          ))}
                        </ul>
                      </div>
                    </td>
                  </tr>
                ) : null}
              </React.Fragment>
            );
          })}

          {rows.length === 0 ? (
            <tr>
              <td colSpan={6} className="px-4 py-16 text-center text-sm text-slate-500">
                조회된 프로젝트가 없습니다.
              </td>
            </tr>
          ) : null}
        </tbody>
      </table>
    </div>
  );
}

function MaintenanceTable({ rows }) {
  const [openedId, setOpenedId] = useState('');

  return (
    <div className="overflow-hidden rounded-xl border border-slate-200">
      <table className="w-full border-collapse text-left text-sm">
        <thead className="bg-slate-100 text-xs font-bold uppercase tracking-wide text-slate-500">
          <tr>
            <th className="w-20 px-4 py-3">NO</th>
            <th className="px-4 py-3">유지보수명</th>
            <th className="px-4 py-3">수행인원</th>
            <th className="px-4 py-3">계약종료</th>
            <th className="px-4 py-3">진행내역 / 이슈</th>
            <th className="px-4 py-3">정기점검 현황</th>
            <th className="w-14 px-4 py-3" />
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-200 bg-white">
          {rows.map((row) => {
            const open = openedId === row.id;

            return (
              <React.Fragment key={row.id}>
                <tr
                  className="cursor-pointer transition hover:bg-slate-50"
                  onClick={() => setOpenedId(open ? '' : row.id)}
                >
                  <td className="px-4 py-4 font-mono text-slate-500">{row.no}</td>
                  <td className="px-4 py-4">
                    <div className="font-bold text-slate-900">{row.customerName}({row.siteCode})</div>
                    <div className="mt-0.5 text-xs text-slate-500">{row.projectType}</div>
                  </td>
                  <td className="px-4 py-4 text-slate-700">{row.executors.join(', ')}</td>
                  <td className="px-4 py-4 font-mono text-slate-600">{row.contractEnd}</td>
                  <td className="px-4 py-4 text-slate-700">{row.latestIssue}</td>
                  <td className="px-4 py-4 text-slate-700">{row.inspection}</td>
                  <td className="px-4 py-4 text-slate-400">
                    <ChevronIcon open={open} />
                  </td>
                </tr>

                {open ? (
                  <tr className="bg-slate-50">
                    <td colSpan={7} className="px-4 py-4">
                      <div className="rounded-xl border border-slate-200 bg-white p-4">
                        <div className="mb-2 text-xs font-bold text-slate-500">상세 진행 내용</div>
                        <ul className="space-y-1 text-sm text-slate-700">
                          {row.detail.map((item) => (
                            <li key={item} className="flex gap-2">
                              <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-blue-500" />
                              <span>{item}</span>
                            </li>
                          ))}
                        </ul>
                      </div>
                    </td>
                  </tr>
                ) : null}
              </React.Fragment>
            );
          })}

          {rows.length === 0 ? (
            <tr>
              <td colSpan={7} className="px-4 py-16 text-center text-sm text-slate-500">
                조회된 유지보수 대상이 없습니다.
              </td>
            </tr>
          ) : null}
        </tbody>
      </table>
    </div>
  );
}

function SearchResultList({ projectRows, maintenanceRows, keyword }) {
  if (!keyword.trim()) {
    return (
      <div className="rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-16 text-center">
        <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-white text-slate-400 shadow-sm">
          <SearchIcon />
        </div>
        <div className="font-semibold text-slate-900">통합 검색 시스템</div>
        <p className="mt-2 text-sm text-slate-500">
          고객사, 수행인원, 작업내역, 인프라 유형 기준으로 프로젝트와 유지보수 데이터를 함께 검색합니다.
        </p>
      </div>
    );
  }

  const results = [
    ...projectRows.map((row) => ({ ...row, category: '프로젝트' })),
    ...maintenanceRows.map((row) => ({ ...row, category: '유지보수' })),
  ];

  return (
    <div className="space-y-3">
      {results.map((row) => (
        <div key={`${row.category}-${row.id}`} className="rounded-xl border border-slate-200 bg-white p-4">
          <div className="mb-2 flex items-center gap-2">
            <span className="rounded-full bg-blue-50 px-2 py-1 text-xs font-bold text-blue-600">{row.category}</span>
            <span className="text-sm font-bold text-slate-900">{row.customerName}({row.siteCode})</span>
          </div>
          <p className="text-sm text-slate-700">{row.latestIssue}</p>
          <p className="mt-2 text-xs text-slate-500">수행인원: {row.executors.join(', ')}</p>
        </div>
      ))}

      {results.length === 0 ? (
        <div className="rounded-xl border border-slate-200 bg-white px-4 py-16 text-center text-sm text-slate-500">
          검색 결과가 없습니다.
        </div>
      ) : null}
    </div>
  );
}

export default function WorkIssueHistoryPage() {
  const [activeInnerTab, setActiveInnerTab] = useState('projects');
  const [keyword, setKeyword] = useState('');
  const [executor, setExecutor] = useState('');
  const [customer, setCustomer] = useState('');
  const [selectedInfraTypes, setSelectedInfraTypes] = useState([]);

  const allRows = useMemo(() => [...PROJECT_ROWS, ...MAINTENANCE_ROWS], []);

  const executorOptions = useMemo(() => {
    return [...new Set(allRows.flatMap((row) => row.executors))].sort();
  }, [allRows]);

  const customerOptions = useMemo(() => {
    return [...new Set(PROJECT_ROWS.map((row) => row.customerName))].sort();
  }, []);

  const filterState = { keyword, executor, customer, infraTypes: selectedInfraTypes };

  const filteredProjects = useMemo(() => {
    return getFilteredRows(PROJECT_ROWS, filterState);
  }, [keyword, executor, customer, selectedInfraTypes]);

  const filteredMaintenance = useMemo(() => {
    return getFilteredRows(MAINTENANCE_ROWS, {
      keyword,
      executor,
      customer: '',
      infraTypes: selectedInfraTypes,
    });
  }, [keyword, executor, selectedInfraTypes]);

  const resetFilters = () => {
    setKeyword('');
    setExecutor('');
    setCustomer('');
    setSelectedInfraTypes([]);
  };

  return (
    <>
      <div className="mb-4 flex items-baseline">
        <h1 className="shrink-0 text-3xl font-bold tracking-tight text-slate-900">
          작업 및 이슈이력
        </h1>
        <p className="ml-16 border-l border-slate-300 pl-8 text-sm text-slate-500">
          프로젝트·유지보수 주간보고 통합 화면입니다.
        </p>
      </div>

      <SectionCard title="" description="">
        <div className="space-y-5">
          <div className="flex flex-wrap items-center gap-2 border-b border-slate-200 pb-4">
            {INNER_TABS.map((tab) => {
              const active = activeInnerTab === tab.key;

              return (
                <button
                  key={tab.key}
                  type="button"
                  onClick={() => setActiveInnerTab(tab.key)}
                  className={`rounded-xl border px-5 py-2.5 text-sm font-bold transition ${
                    active
                      ? 'border-blue-600 bg-blue-50 text-blue-600 shadow-sm'
                      : 'border-transparent text-slate-500 hover:bg-slate-100 hover:text-slate-900'
                  }`}
                >
                  {tab.label}
                </button>
              );
            })}

            <button
              type="button"
              className="ml-auto inline-flex items-center gap-2 rounded-lg border border-blue-200 bg-blue-50 px-4 py-2 text-xs font-bold text-blue-600 transition hover:bg-blue-100"
              title="현재 단계에서는 화면 버튼만 배치했습니다. 실제 엑셀 업로드 기능은 추후 API/파싱 로직 연결 시 추가합니다."
            >
              <UploadIcon />
              Excel Import
            </button>
          </div>

          <WorkIssueFilterBar
            keyword={keyword}
            setKeyword={setKeyword}
            executor={executor}
            setExecutor={setExecutor}
            customer={customer}
            setCustomer={setCustomer}
            selectedInfraTypes={selectedInfraTypes}
            setSelectedInfraTypes={setSelectedInfraTypes}
            executorOptions={executorOptions}
            customerOptions={customerOptions}
            showCustomerFilter={activeInnerTab === 'projects'}
            onReset={resetFilters}
          />

          {activeInnerTab === 'projects' ? <ProjectTable rows={filteredProjects} /> : null}
          {activeInnerTab === 'maintenance' ? <MaintenanceTable rows={filteredMaintenance} /> : null}
          {activeInnerTab === 'search' ? (
            <SearchResultList
              projectRows={filteredProjects}
              maintenanceRows={filteredMaintenance}
              keyword={keyword}
            />
          ) : null}
        </div>
      </SectionCard>
    </>
  );
}
