import React, { useEffect, useMemo, useRef, useState } from 'react';
import SectionCard from '../components/common/SectionCard';
import { API_BASE } from '../constants/patchHistoryOptions';

// 작업 및 이슈이력 API 기본 경로입니다.
// 프론트는 엑셀 파일만 전송하고, 실제 파싱/DB 저장은 Spring Boot에서 처리합니다.
const WORK_ISSUE_API = `${API_BASE}/api/work-issue-histories`;

// 현재 화면 안쪽에서 사용하는 탭입니다. 기존 상단 메뉴 구조는 건드리지 않습니다.
const INNER_TABS = [
  { key: 'projects', label: '프로젝트 현황' },
  { key: 'maintenance', label: '유지보수 현황' },
  { key: 'search', label: '통합검색' },
];

const INFRA_TYPES = ['SMS', 'NMS', 'DBMS', 'APM'];

// 한 화면에 너무 많은 행을 렌더링하면 행 클릭 시 브라우저 리플로우가 커집니다.
// 기본 50건 단위로 끊어서 클릭/펼침 반응 속도를 안정화합니다.
const DEFAULT_PAGE_SIZE = 50;
const PAGE_SIZE_OPTIONS = [20, 50, 100];
const MAX_DETAIL_LINES = 80;

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

// 백엔드 ApiResponse 형식({ success, data, message })에서 data만 꺼냅니다.
async function readApiResponse(response, fallbackMessage) {
  const result = await response.json().catch(() => null);

  if (!response.ok || result?.success === false) {
    throw new Error(result?.message || fallbackMessage);
  }

  return result?.data ?? result;
}

// null/undefined가 화면에 그대로 찍히지 않게 문자열로 보정합니다.
function text(value) {
  return value == null ? '' : String(value).trim();
}

function number(value) {
  const parsed = Number(value || 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

function formatDateTime(value) {
  if (!value) return '-';
  return String(value).replace('T', ' ').slice(0, 16);
}

// 수행인원 문자열을 화면 필터에서 쓰기 좋은 배열로 변환합니다.
function splitPeople(value) {
  return text(value)
    .split(/[,/·\n]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function firstMeaningfulLine(value) {
  return text(value)
    .split('\n')
    .map((line) => line.replace(/^[\s\-–·>▶:]+/, '').trim())
    .find((line) => line.length > 0) || '';
}

function splitDetailLines(...values) {
  return values
    .flatMap((value) => text(value).split('\n'))
    .map((line) => line.replace(/^[\s\-–·>▶:]+/, '').trim())
    .filter(Boolean);
}

// 프로젝트 행에서 인프라 유형을 추정합니다. DB에는 원문을 보존하고, 화면 필터용으로만 계산합니다.
function inferProjectInfraTypes(project) {
  const target = [project.scope, project.apm, project.dashboard, project.oz, project.progressLogs, project.remainingIssues]
    .join(' ')
    .toLowerCase();

  return INFRA_TYPES.filter((infra) => {
    const key = infra.toLowerCase();
    if (infra === 'APM') return target.includes('apm') || text(project.apm).toUpperCase() === 'O';
    if (infra === 'DBMS') return target.includes('db') || target.includes('pg') || target.includes('postgres');
    return target.includes(key);
  });
}

// 유지보수 행에서 인프라 유형을 추정합니다. SMS/NMS는 사용/전체 값이 있으면 선택 대상으로 봅니다.
function inferMaintenanceInfraTypes(item) {
  const result = [];
  if (text(item.smsStatus) && text(item.smsStatus).toUpperCase() !== 'X') result.push('SMS');
  if (text(item.nmsStatus) && text(item.nmsStatus).toUpperCase() !== 'X') result.push('NMS');
  if (text(item.pgVersion) && text(item.pgVersion).toUpperCase() !== 'X') result.push('DBMS');
  if (text(item.apm).toUpperCase() === 'O') result.push('APM');
  return result;
}

// DB 프로젝트 응답을 기존 화면 레이아웃이 쓰던 형태로 변환합니다.
function normalizeProject(project) {
  const executors = splitPeople(project.executors || project.salesRep);
  const detail = splitDetailLines(project.progressLogs, project.remainingIssues);

  return {
    ...project,
    id: `project-${project.id || project.rowNo}`,
    no: text(project.no || project.rowNo),
    customerName: text(project.clientName),
    siteCode: text(project.siteCode),
    projectType: text(project.projectScale || project.scope),
    executors,
    startDate: text(project.startDate),
    infraTypes: inferProjectInfraTypes(project),
    latestIssue: firstMeaningfulLine(project.progressLogs) || firstMeaningfulLine(project.remainingIssues) || '-',
    detail: detail.length ? detail : ['상세 진행 내용이 없습니다.'],
    updatedAt: formatDateTime(project.createdAt),
  };
}

// DB 유지보수 응답을 기존 화면 레이아웃이 쓰던 형태로 변환합니다.
function normalizeMaintenance(item) {
  const executors = splitPeople([item.mainDev, item.subDev].filter(Boolean).join(','));
  const inspection = Object.entries(item.inspectionDates || {})
    .map(([month, date]) => `${month} ${date}`)
    .join(', ');
  const detail = splitDetailLines(item.progressIssues, item.remarks);

  return {
    ...item,
    id: `maintenance-${item.id || item.rowNo}`,
    no: text(item.no || item.rowNo),
    customerName: text(item.maintenanceName),
    siteCode: text(item.siteCode),
    projectType: text(item.contractType || item.visitType || item.method),
    executors,
    contractEnd: text(item.contractEnd),
    infraTypes: inferMaintenanceInfraTypes(item),
    latestIssue: firstMeaningfulLine(item.progressIssues) || firstMeaningfulLine(item.remarks) || '-',
    inspection: inspection || '-',
    detail: detail.length ? detail : ['상세 진행 내용이 없습니다.'],
    updatedAt: formatDateTime(item.createdAt),
  };
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
    <div className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm">
      <div className="flex flex-wrap items-end gap-2">
        <div className="min-w-[220px] flex-1">
          <label className="mb-1 block text-xs font-medium text-slate-700">검색어 (AND 조건)</label>
          <div className="relative">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">
              <SearchIcon />
            </span>
            <input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="검색어 입력..."
              className="h-9 w-full rounded-lg border border-slate-300 bg-white pl-9 pr-3 text-sm outline-none ring-0 transition focus:border-slate-500"
            />
          </div>
        </div>

        <div className="w-[180px]">
          <label className="mb-1 block text-xs font-medium text-slate-700">수행인원</label>
          <select
            value={executor}
            onChange={(event) => setExecutor(event.target.value)}
            className="h-9 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm outline-none ring-0 transition focus:border-slate-500"
          >
            <option value="">전체 수행인원</option>
            {executorOptions.map((name) => (
              <option key={name} value={name}>{name}</option>
            ))}
          </select>
        </div>

        {showCustomerFilter ? (
          <div className="w-[180px]">
            <label className="mb-1 block text-xs font-medium text-slate-700">고객사</label>
            <select
              value={customer}
              onChange={(event) => setCustomer(event.target.value)}
              className="h-9 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm outline-none ring-0 transition focus:border-slate-500"
            >
              <option value="">전체 고객사</option>
              {customerOptions.map((name) => (
                <option key={name} value={name}>{name}</option>
              ))}
            </select>
          </div>
        ) : null}

        <div>
          <label className="mb-1 block text-xs font-medium text-slate-700">인프라 필터</label>
          <div className="flex flex-wrap gap-2">
            {INFRA_TYPES.map((infra) => {
              const selected = selectedInfraTypes.includes(infra);

              return (
                <button
                  key={infra}
                  type="button"
                  onClick={() => toggleInfra(infra)}
                  className={`h-9 rounded-lg border px-3 text-sm font-semibold transition ${
                    selected
                      ? 'border-slate-900 bg-slate-900 text-white'
                      : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
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
          className="ml-auto inline-flex h-9 items-center gap-2 rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50"
        >
          <FilterIcon />
          초기화
        </button>
      </div>
    </div>
  );
}

function TablePagination({ page, pageSize, totalCount, onPageChange, onPageSizeChange }) {
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
  const startNo = totalCount === 0 ? 0 : (page - 1) * pageSize + 1;
  const endNo = Math.min(totalCount, page * pageSize);

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 bg-slate-50 px-4 py-3 text-xs text-slate-500">
      <div>
        총 <b className="text-slate-700">{totalCount}</b>건 중 <b className="text-slate-700">{startNo}</b>-<b className="text-slate-700">{endNo}</b> 표시
      </div>

      <div className="flex items-center gap-2">
        <select
          value={pageSize}
          onChange={(event) => onPageSizeChange(Number(event.target.value))}
          className="h-8 rounded-lg border border-slate-300 bg-white px-2 text-xs outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
          title="페이지당 표시 건수"
        >
          {PAGE_SIZE_OPTIONS.map((size) => (
            <option key={size} value={size}>{size}건</option>
          ))}
        </select>

        <button
          type="button"
          disabled={page <= 1}
          onClick={() => onPageChange(1)}
          className="h-8 rounded-lg border border-slate-300 bg-white px-2 font-bold text-slate-600 disabled:cursor-not-allowed disabled:opacity-40"
        >
          &lt;&lt;
        </button>
        <button
          type="button"
          disabled={page <= 1}
          onClick={() => onPageChange(page - 1)}
          className="h-8 rounded-lg border border-slate-300 bg-white px-2 font-bold text-slate-600 disabled:cursor-not-allowed disabled:opacity-40"
        >
          &lt;
        </button>
        <span className="min-w-[72px] text-center font-semibold text-slate-600">
          {page} / {totalPages}
        </span>
        <button
          type="button"
          disabled={page >= totalPages}
          onClick={() => onPageChange(page + 1)}
          className="h-8 rounded-lg border border-slate-300 bg-white px-2 font-bold text-slate-600 disabled:cursor-not-allowed disabled:opacity-40"
        >
          &gt;
        </button>
        <button
          type="button"
          disabled={page >= totalPages}
          onClick={() => onPageChange(totalPages)}
          className="h-8 rounded-lg border border-slate-300 bg-white px-2 font-bold text-slate-600 disabled:cursor-not-allowed disabled:opacity-40"
        >
          &gt;&gt;
        </button>
      </div>
    </div>
  );
}

function usePaginatedRows(rows) {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  const totalPages = Math.max(1, Math.ceil(rows.length / pageSize));
  const safePage = Math.min(page, totalPages);

  useEffect(() => {
    // 검색/필터 변경으로 목록이 바뀌면 첫 페이지부터 다시 보여줍니다.
    setPage(1);
  }, [rows]);

  const pagedRows = useMemo(() => {
    const startIndex = (safePage - 1) * pageSize;
    return rows.slice(startIndex, startIndex + pageSize);
  }, [rows, safePage, pageSize]);

  const changePage = (nextPage) => {
    const next = Math.min(Math.max(nextPage, 1), totalPages);
    setPage(next);
  };

  const changePageSize = (nextPageSize) => {
    setPageSize(nextPageSize);
    setPage(1);
  };

  return {
    page: safePage,
    pageSize,
    pagedRows,
    totalCount: rows.length,
    changePage,
    changePageSize,
  };
}

function DetailPanel({ rowId, detail, colSpan }) {
  const visibleDetail = detail.slice(0, MAX_DETAIL_LINES);
  const hiddenCount = Math.max(0, detail.length - MAX_DETAIL_LINES);

  return (
    <tr className="bg-slate-50">
      <td colSpan={colSpan} className="px-4 py-4">
        <div className="max-h-[320px] overflow-y-auto rounded-xl border border-slate-200 bg-white p-4">
          <div className="mb-2 text-xs font-bold text-slate-500">상세 진행 내용</div>
          <ul className="space-y-1 text-sm text-slate-700">
            {visibleDetail.map((item, index) => (
              <li key={`${rowId}-${index}`} className="flex gap-2">
                <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-blue-500" />
                <span>{item}</span>
              </li>
            ))}
          </ul>
          {hiddenCount > 0 ? (
            <div className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-xs font-semibold text-slate-500">
              상세 내용이 길어 처음 {MAX_DETAIL_LINES}줄만 표시했습니다. 남은 {hiddenCount}줄은 별도 상세 화면으로 분리하는 편이 안전합니다.
            </div>
          ) : null}
        </div>
      </td>
    </tr>
  );
}

const ProjectTableRow = React.memo(function ProjectTableRow({ row, open, onToggle }) {
  return (
    <React.Fragment>
      <tr
        className="cursor-pointer transition hover:bg-slate-50"
        onClick={() => onToggle(row.id)}
      >
        <td className="px-4 py-4 font-mono text-slate-500">{row.no}</td>
        <td className="px-4 py-4">
          <div className="font-bold text-slate-900">{row.customerName}{row.siteCode ? `(${row.siteCode})` : ''}</div>
          <div className="mt-0.5 text-xs text-slate-500">{row.projectType || '-'}</div>
        </td>
        <td className="px-4 py-4 truncate whitespace-nowrap text-slate-700" title={row.executors.join(', ')}>{row.executors.join(', ') || '-'}</td>
        <td className="px-4 py-4 whitespace-nowrap font-mono text-slate-600">{row.startDate || '-'}</td>
        <td className="px-4 py-4 text-slate-700">{row.latestIssue}</td>
        <td className="px-4 py-4 text-slate-400">
          <ChevronIcon open={open} />
        </td>
      </tr>

      {open ? <DetailPanel rowId={row.id} detail={row.detail} colSpan={6} /> : null}
    </React.Fragment>
  );
});

function ProjectTable({ rows }) {
  const [openedId, setOpenedId] = useState('');
  const { page, pageSize, pagedRows, totalCount, changePage, changePageSize } = usePaginatedRows(rows);

  useEffect(() => {
    setOpenedId('');
  }, [rows, page, pageSize]);

  const toggleRow = (rowId) => {
    setOpenedId((prev) => (prev === rowId ? '' : rowId));
  };

  return (
    <div className="overflow-hidden rounded-xl border border-slate-200">
      <table className="w-full table-fixed border-collapse text-left text-sm">
        {/* 수행인원 컬럼이 줄바꿈되지 않도록 고객사/진행사항 폭을 일부 줄이고 수행인원 폭을 확보합니다. */}
        <colgroup>
          <col className="w-[5%]" />
          <col className="w-[43%]" />
          <col className="w-[16%]" />
          <col className="w-[11%]" />
          <col className="w-[22%]" />
          <col className="w-[3%]" />
        </colgroup>
        <thead className="bg-slate-100 text-xs font-bold uppercase tracking-wide text-slate-500">
          <tr>
            <th className="px-4 py-3 whitespace-nowrap">NO</th>
            <th className="px-4 py-3">고객사(사업명)</th>
            <th className="px-4 py-3 whitespace-nowrap">수행인원</th>
            <th className="px-4 py-3 whitespace-nowrap">시작일</th>
            <th className="px-4 py-3">금주 진행사항 (최신)</th>
            <th className="px-4 py-3" />
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-200 bg-white">
          {pagedRows.map((row) => (
            <ProjectTableRow
              key={row.id}
              row={row}
              open={openedId === row.id}
              onToggle={toggleRow}
            />
          ))}

          {rows.length === 0 ? (
            <tr>
              <td colSpan={6} className="px-4 py-16 text-center text-sm text-slate-500">
                조회된 프로젝트가 없습니다.
              </td>
            </tr>
          ) : null}
        </tbody>
      </table>

      {rows.length > 0 ? (
        <TablePagination
          page={page}
          pageSize={pageSize}
          totalCount={totalCount}
          onPageChange={changePage}
          onPageSizeChange={changePageSize}
        />
      ) : null}
    </div>
  );
}

const MaintenanceTableRow = React.memo(function MaintenanceTableRow({ row, open, onToggle }) {
  return (
    <React.Fragment>
      <tr
        className="cursor-pointer transition hover:bg-slate-50"
        onClick={() => onToggle(row.id)}
      >
        <td className="px-4 py-4 font-mono text-slate-500">{row.no}</td>
        <td className="px-4 py-4">
          <div className="font-bold text-slate-900">{row.customerName}{row.siteCode ? `(${row.siteCode})` : ''}</div>
          <div className="mt-0.5 text-xs text-slate-500">{row.projectType || '-'}</div>
        </td>
        <td className="px-4 py-4 truncate whitespace-nowrap text-slate-700" title={row.executors.join(', ')}>{row.executors.join(', ') || '-'}</td>
        <td className="px-4 py-4 whitespace-nowrap font-mono text-slate-600">{row.contractEnd || '-'}</td>
        <td className="px-4 py-4 text-slate-700">{row.latestIssue}</td>
        <td className="px-4 py-4 text-slate-700">{row.inspection}</td>
        <td className="px-4 py-4 text-slate-400">
          <ChevronIcon open={open} />
        </td>
      </tr>

      {open ? <DetailPanel rowId={row.id} detail={row.detail} colSpan={7} /> : null}
    </React.Fragment>
  );
});

function MaintenanceTable({ rows }) {
  const [openedId, setOpenedId] = useState('');
  const { page, pageSize, pagedRows, totalCount, changePage, changePageSize } = usePaginatedRows(rows);

  useEffect(() => {
    setOpenedId('');
  }, [rows, page, pageSize]);

  const toggleRow = (rowId) => {
    setOpenedId((prev) => (prev === rowId ? '' : rowId));
  };

  return (
    <div className="overflow-hidden rounded-xl border border-slate-200">
      <table className="w-full table-fixed border-collapse text-left text-sm">
        {/* 계약종료 컬럼 날짜가 2026-12-31처럼 한 줄로 보이도록 폭을 고정합니다. */}
        <colgroup>
          <col className="w-[5%]" />
          <col className="w-[31%]" />
          <col className="w-[12%]" />
          <col className="w-[12%]" />
          <col className="w-[18%]" />
          <col className="w-[19%]" />
          <col className="w-[3%]" />
        </colgroup>
        <thead className="bg-slate-100 text-xs font-bold uppercase tracking-wide text-slate-500">
          <tr>
            <th className="px-4 py-3 whitespace-nowrap">NO</th>
            <th className="px-4 py-3">유지보수명</th>
            <th className="px-4 py-3 whitespace-nowrap">수행인원</th>
            <th className="px-4 py-3 whitespace-nowrap">계약종료</th>
            <th className="px-4 py-3">진행내역 / 이슈</th>
            <th className="px-4 py-3">정기점검 현황</th>
            <th className="px-4 py-3" />
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-200 bg-white">
          {pagedRows.map((row) => (
            <MaintenanceTableRow
              key={row.id}
              row={row}
              open={openedId === row.id}
              onToggle={toggleRow}
            />
          ))}

          {rows.length === 0 ? (
            <tr>
              <td colSpan={7} className="px-4 py-16 text-center text-sm text-slate-500">
                조회된 유지보수 대상이 없습니다.
              </td>
            </tr>
          ) : null}
        </tbody>
      </table>

      {rows.length > 0 ? (
        <TablePagination
          page={page}
          pageSize={pageSize}
          totalCount={totalCount}
          onPageChange={changePage}
          onPageSizeChange={changePageSize}
        />
      ) : null}
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
            <span className="text-sm font-bold text-slate-900">{row.customerName}{row.siteCode ? `(${row.siteCode})` : ''}</span>
          </div>
          <p className="text-sm text-slate-700">{row.latestIssue}</p>
          <p className="mt-2 text-xs text-slate-500">수행인원: {row.executors.join(', ') || '-'}</p>
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
  const fileInputRef = useRef(null);

  const [activeInnerTab, setActiveInnerTab] = useState('projects');
  const [uploads, setUploads] = useState([]);
  const [selectedUploadId, setSelectedUploadId] = useState('');
  const [summary, setSummary] = useState(null);
  const [projects, setProjects] = useState([]);
  const [maintenanceRows, setMaintenanceRows] = useState([]);

  const [keyword, setKeyword] = useState('');
  const [executor, setExecutor] = useState('');
  const [customer, setCustomer] = useState('');
  const [selectedInfraTypes, setSelectedInfraTypes] = useState([]);

  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  async function fetchWorkIssueData(uploadId = '') {
    setLoading(true);
    setError('');

    try {
      const uploadList = await fetch(`${WORK_ISSUE_API}/uploads`)
        .then((response) => readApiResponse(response, '업로드 이력 조회에 실패했습니다.'));

      const resolvedUploadId = uploadId || (uploadList?.[0]?.uploadId ? String(uploadList[0].uploadId) : '');
      const query = resolvedUploadId ? `?uploadId=${resolvedUploadId}` : '';

      const [summaryData, projectData, maintenanceData] = await Promise.all([
        fetch(`${WORK_ISSUE_API}/summary${query}`).then((response) => readApiResponse(response, '요약 조회에 실패했습니다.')),
        fetch(`${WORK_ISSUE_API}/projects${query}`).then((response) => readApiResponse(response, '프로젝트 조회에 실패했습니다.')),
        fetch(`${WORK_ISSUE_API}/maintenance${query}`).then((response) => readApiResponse(response, '유지보수 조회에 실패했습니다.')),
      ]);

      setUploads(uploadList || []);
      setSelectedUploadId(resolvedUploadId);
      setSummary(summaryData);
      setProjects((projectData || []).map(normalizeProject));
      setMaintenanceRows((maintenanceData || []).map(normalizeMaintenance));
    } catch (e) {
      setError(e.message || '작업 및 이슈이력 조회 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    fetchWorkIssueData('');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function handleFileUpload(event) {
    const file = event.target.files?.[0];
    if (!file) return;

    const formData = new FormData();
    formData.append('file', file);

    setUploading(true);
    setMessage('');
    setError('');

    try {
      const uploaded = await fetch(`${WORK_ISSUE_API}/upload`, {
        method: 'POST',
        body: formData,
      }).then((response) => readApiResponse(response, '엑셀 업로드에 실패했습니다.'));

      const uploadedId = uploaded?.uploadId ? String(uploaded.uploadId) : '';
      setMessage(`엑셀 업로드 완료: 프로젝트 ${uploaded?.projectCount || 0}건, 유지보수 ${uploaded?.maintenanceCount || 0}건 저장`);
      await fetchWorkIssueData(uploadedId);
    } catch (e) {
      setError(e.message || '엑셀 업로드 중 오류가 발생했습니다.');
    } finally {
      setUploading(false);
      event.target.value = '';
    }
  }

  const allRows = useMemo(() => [...projects, ...maintenanceRows], [projects, maintenanceRows]);

  const executorOptions = useMemo(() => {
    return [...new Set(allRows.flatMap((row) => row.executors))].sort();
  }, [allRows]);

  const customerOptions = useMemo(() => {
    return [...new Set(projects.map((row) => row.customerName).filter(Boolean))].sort();
  }, [projects]);

  const filterState = { keyword, executor, customer, infraTypes: selectedInfraTypes };

  const filteredProjects = useMemo(() => {
    return getFilteredRows(projects, filterState);
  }, [projects, keyword, executor, customer, selectedInfraTypes]);

  const filteredMaintenance = useMemo(() => {
    return getFilteredRows(maintenanceRows, {
      keyword,
      executor,
      customer: '',
      infraTypes: selectedInfraTypes,
    });
  }, [maintenanceRows, keyword, executor, selectedInfraTypes]);

  const totalMd = number(summary?.projectMdTotal) + number(summary?.maintenanceMdTotal);

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

            <div className="ml-auto flex flex-wrap items-center gap-2">
              {uploads.length > 0 ? (
                <select
                  value={selectedUploadId}
                  onChange={(event) => fetchWorkIssueData(event.target.value)}
                  className="h-10 max-w-[300px] rounded-lg border border-slate-300 bg-white px-3 text-xs font-medium text-slate-600 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                  title="업로드 이력 선택"
                >
                  {uploads.map((upload) => (
                    <option key={upload.uploadId} value={String(upload.uploadId)}>
                      {upload.reportWeek || formatDateTime(upload.createdAt)} · {upload.originalFileName}
                    </option>
                  ))}
                </select>
              ) : null}

              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={uploading}
                className="inline-flex h-9 w-[132px] items-center justify-center gap-2 whitespace-nowrap rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.8"
                  className="h-5 w-5 text-emerald-600"
                  aria-hidden="true"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" />
                  <path strokeLinecap="round" strokeLinejoin="round" d="M14 3v5h5" />
                  <path strokeLinecap="round" strokeLinejoin="round" d="m9 11 6 6m0-6-6 6" />
                </svg>
                {uploading ? '업로드 중...' : '엑셀 업로드'}
              </button>
              <input
                ref={fileInputRef}
                type="file"
                accept=".xlsx,.xls"
                className="hidden"
                onChange={handleFileUpload}
              />
            </div>
          </div>

          {(message || error || loading || summary) ? (
            <div className="flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-xs text-slate-600">
              {error ? <span className="font-semibold text-red-600">{error}</span> : null}
              {!error && message ? <span className="font-semibold text-blue-600">{message}</span> : null}
              {!error && !message && loading ? <span className="font-semibold text-slate-500">데이터를 조회 중입니다.</span> : null}
              {!error && !message && !loading && summary ? (
                <span>
                  기준 파일: <b>{summary.originalFileName || '-'}</b> · 프로젝트 <b>{summary.projectCount || 0}</b>건 · 유지보수 <b>{summary.maintenanceCount || 0}</b>건 · 공수 <b>{totalMd.toFixed(1)}</b>M/D
                </span>
              ) : null}
            </div>
          ) : null}

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
