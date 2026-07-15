import React, { useEffect, useMemo, useRef, useState } from 'react';
import SectionCard from '../components/common/SectionCard';
import LabeledInput from '../components/common/LabeledInput';
import SaveScreenButton from '../components/common/SaveScreenButton';
import MaintenanceSupportScope from '../components/workIssue/MaintenanceSupportScope';
import { API_BASE } from '../constants/patchHistoryOptions';
import { getMaintenancePopupFeatures } from '../utils/maintenancePopup';
import { parseTimelineDateHeader } from '../utils/timelineDates';

// 작업 및 이슈이력 API 기본 경로입니다.
// 프론트는 엑셀 파일만 전송하고, 실제 파싱/DB 저장은 Spring Boot에서 처리합니다.
const WORK_ISSUE_API = `${API_BASE}/api/work-issue-histories`;

// 현재 화면 안쪽에서 사용하는 탭입니다. 기존 상단 메뉴 구조는 건드리지 않습니다.
const INNER_TABS = [
  { key: 'search', label: '통합이력검색', subLabel: '(프로젝트+유지보수)' },
  { key: 'projects', label: '프로젝트' },
  { key: 'maintenance', label: '유지보수' },
];

const INFRA_TYPES = [
  'ERMS',
  'SMS',
  'NMS',
  'DBMS',
  'FMS',
  'IMS',
  'SYSLOG',
  'TRAP',
  'TMS',
  'APM',
  'BMS',
  'STMS',
  'RTMS',
  'VMS',
  'OAM',
  'WNMS',
  'CMS',
  'K8s',
  'TRMS',
  'NPM',
  'BRMS',
];

// 한 화면에 너무 많은 행을 렌더링하면 행 클릭 시 브라우저 리플로우가 커집니다.
// 기본 50건 단위로 끊어서 클릭/펼침 반응 속도를 안정화합니다.
const DEFAULT_PAGE_SIZE = 50;
const PAGE_SIZE_OPTIONS = [20, 50, 100];
const MAX_DETAIL_LINES = 80;
const searchInputClass =
  'h-9 w-full rounded-lg border border-slate-300 px-3 text-sm outline-none ring-0 focus:border-slate-500';

function getInitialSearchParam(name, fallback = '') {
  const params = new URLSearchParams(window.location.search);
  return params.get(name) || fallback;
}

function getInitialWorkIssueTab() {
  const params = new URLSearchParams(window.location.search);
  const tab = params.get('tab');
  const workIssueType = params.get('workIssueType');
  const keyword = params.get('keyword');

  if (INNER_TABS.some((item) => item.key === tab)) {
    return tab;
  }
  if (keyword) {
    return 'search';
  }
  if (workIssueType === 'MAINTENANCE') {
    return 'maintenance';
  }

  return 'search';
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

function PopupIcon() {
  return (
    <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M14 3h7v7" />
      <path d="M10 14 21 3" />
      <path d="M21 14v5a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5" />
    </svg>
  );
}

function DetailPopupButton({ disabled, onOpen }) {
  const handleClick = (event) => {
    event.stopPropagation();

    if (!disabled) {
      onOpen();
    }
  };

  return (
    <button
      type="button"
      onClick={handleClick}
      disabled={disabled}
      title="상세 팝업 열기"
      aria-label="상세 팝업 열기"
      className="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-md border border-slate-200 bg-white text-slate-500 shadow-sm transition hover:border-blue-200 hover:bg-blue-50 hover:text-blue-600 disabled:cursor-not-allowed disabled:opacity-40"
    >
      <PopupIcon />
    </button>
  );
}

function openWorkIssueHistoryDetailWindow(type, row) {
  const ids = row?.sourceIds?.length ? row.sourceIds : [row?.detailId].filter(Boolean);

  if (ids.length === 0) {
    return;
  }

  const resolvedType = type === 'MAINTENANCE' ? 'MAINTENANCE' : 'PROJECT';
  const encodedIds = encodeURIComponent(ids.join(','));
  const url = `${window.location.origin}${window.location.pathname}?popup=work-issue-history-detail&type=${resolvedType}&ids=${encodedIds}`;
  const features = resolvedType === 'MAINTENANCE'
    ? getMaintenancePopupFeatures()
    : 'width=1200,height=820,left=120,top=80,scrollbars=yes,resizable=yes';
  const windowName = resolvedType === 'MAINTENANCE'
    ? '_blank'
    : `work-issue-history-detail-${resolvedType}-${ids.join('-')}`;

  window.open(url, windowName, features);
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

function normalizeUploadFileName(fileName) {
  return text(fileName).toLowerCase();
}

function uniqueUploadList(uploadList) {
  const uniqueUploads = new Map();

  (uploadList || []).forEach((upload) => {
    const key = normalizeUploadFileName(upload.originalFileName);

    if (!key || uniqueUploads.has(key)) {
      return;
    }

    uniqueUploads.set(key, upload);
  });

  return Array.from(uniqueUploads.values());
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function getKeywordTerms(keyword) {
  return text(keyword)
    .split(/\s+/)
    .map((word) => word.trim())
    .filter(Boolean);
}

function keywordPattern(term) {
  const escaped = escapeRegExp(term);
  return new RegExp(escaped, 'i');
}

function includesKeyword(value, term) {
  return keywordPattern(term).test(text(value));
}

function highlightText(value, keyword) {
  const source = text(value);
  const terms = getKeywordTerms(keyword);

  if (!source || terms.length === 0) {
    return source;
  }

  const pattern = new RegExp(
    `(${terms
      .map((term) => escapeRegExp(term))
      .join('|')})`,
    'gi'
  );
  return source.split(pattern).map((part, index) => {
    const matched = terms.some((term) => includesKeyword(part, term));

    if (!matched) {
      return part;
    }

    return (
      <mark
        key={`${part}-${index}`}
        className="rounded bg-yellow-100 px-0.5 text-yellow-900 ring-1 ring-yellow-200"
      >
        {part}
      </mark>
    );
  });
}

function number(value) {
  const parsed = Number(value || 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

function formatMetric(value) {
  const numeric = number(value);
  return Number.isInteger(numeric) ? String(numeric) : numeric.toFixed(1).replace(/\.0$/, '');
}

function maintenanceAssigneeText(row) {
  return text(row.mainDev) || '-';
}

function formatDateTime(value) {
  if (!value) return '-';
  return String(value).replace('T', ' ').slice(0, 16);
}

// 수행인원 문자열을 화면 필터에서 쓰기 좋은 배열로 변환합니다.
const EXCLUDED_EXECUTOR_NAMES = new Set(['-', '박천웅', '박신후', '배동훈', '안형락', '베동훈', '박기열', '김도형', '오재근']);

function splitPeople(value) {
  return text(value)
    .split(/[,/·\n]/)
    .map((item) => item.trim())
    .filter((item) => item && !EXCLUDED_EXECUTOR_NAMES.has(item));
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
    if (infra === 'K8s') return target.includes('k8s') || target.includes('kubernetes');
    return target.includes(key);
  });
}

// 유지보수 행에서 인프라 유형을 추정합니다. SMS/NMS는 사용/전체 값이 있으면 선택 대상으로 봅니다.
function inferMaintenanceInfraTypes(item) {
  const result = [];
  if (text(item.smsStatus) && text(item.smsStatus).toUpperCase() !== 'X') result.push('SMS');
  if (text(item.nmsStatus) && text(item.nmsStatus).toUpperCase() !== 'X') result.push('NMS');
  if (text(item.apm).toUpperCase() === 'O') result.push('APM');

  const target = [
    item.maintenanceName,
    item.progressIssues,
    item.remarks,
    item.smsStatus,
    item.nmsStatus,
    item.pgVersion,
    item.webVersion,
    item.region,
  ]
    .join(' ')
    .toLowerCase();

  INFRA_TYPES.forEach((infra) => {
    const key = infra.toLowerCase();
    const matched = infra === 'K8s'
      ? target.includes('k8s') || target.includes('kubernetes')
      : target.includes(key);

    if (matched && !result.includes(infra)) {
      result.push(infra);
    }
  });

  return result;
}

// DB 프로젝트 응답을 기존 화면 레이아웃이 쓰던 형태로 변환합니다.
function normalizeProject(project) {
  const executors = splitPeople(project.executors);
  const detail = splitDetailLines(project.progressLogs, project.remainingIssues);

  return {
    ...project,
    id: `project-${project.id || project.rowNo}`,
    detailId: project.id,
    workHistoryType: 'PROJECT',
    no: text(project.no || project.rowNo),
    customerName: text(project.clientName),
    siteCode: text(project.siteCode),
    salesRep: text(project.salesRep),
    projectType: text(project.projectScale || project.scope),
    executors,
    startDate: text(project.startDate),
    infraTypes: inferProjectInfraTypes(project),
    latestIssue: firstMeaningfulLine(project.progressLogs) || firstMeaningfulLine(project.remainingIssues) || '-',
    detail: detail.length ? detail : ['상세 진행 내용이 없습니다.'],
    updatedAt: formatDateTime(project.createdAt),
    projectLogSources: [
      {
        executor: executors.join(', '),
        progressLogs: text(project.progressLogs),
        visits: number(project.visits),
        md: number(project.md),
        createdAt: project.createdAt,
      },
    ],
  };
}

// DB 유지보수 응답을 기존 화면 레이아웃이 쓰던 형태로 변환합니다.
function mergeTextLines(...values) {
  return [
    ...new Set(
      values
        .flatMap((value) => text(value).split('\n'))
        .map((line) => line.trim())
        .filter(Boolean),
    ),
  ].join('\n');
}

function mergeUniqueList(...lists) {
  return [
    ...new Set(
      lists
        .flatMap((list) => (Array.isArray(list) ? list : splitPeople(list)))
        .map((item) => text(item))
        .filter(Boolean),
    ),
  ];
}

function projectGroupKey(row) {
  const siteCode = text(row.siteCode).toLowerCase();

  if (siteCode && siteCode !== '-') {
    return `site:${siteCode}`;
  }

  return `name:${text(row.customerName).toLowerCase()}`;
}

function groupProjectRows(rows) {
  const grouped = new Map();

  rows.forEach((row) => {
    const key = projectGroupKey(row);
    const existing = grouped.get(key);

    if (!existing) {
      grouped.set(key, {
        ...row,
        id: `project-group-${key}`,
        sourceIds: [row.detailId].filter(Boolean),
        projectLogSources: row.projectLogSources || [],
      });
      return;
    }

    const progressLogs = mergeTextLines(existing.progressLogs, row.progressLogs);
    const remainingIssues = mergeTextLines(existing.remainingIssues, row.remainingIssues);
    const detail = splitDetailLines(progressLogs, remainingIssues);

    grouped.set(key, {
      ...existing,
      sourceIds: [...(existing.sourceIds || []), row.detailId].filter(Boolean),
      salesRep: mergeUniqueList(existing.salesRep, row.salesRep).join(', '),
      executors: mergeUniqueList(existing.executors, row.executors),
      infraTypes: mergeUniqueList(existing.infraTypes, row.infraTypes),
      progressLogs,
      remainingIssues,
      md: number(existing.md) + number(row.md),
      latestIssue: firstMeaningfulLine(progressLogs) || firstMeaningfulLine(remainingIssues) || '-',
      detail: detail.length ? detail : existing.detail,
      projectLogSources: [
        ...(existing.projectLogSources || []),
        ...(row.projectLogSources || []),
      ],
    });
  });

  return [...grouped.values()];
}

function formatInspectionDates(inspectionDates) {
  const groupedByYear = new Map();

  Object.values(inspectionDates || {}).forEach((date) => {
    const matched = text(date).match(/^(\d{4})-(\d{2})-(\d{2})$/);

    if (!matched) {
      return;
    }

    const [, year, month, day] = matched;
    const dates = groupedByYear.get(year) || [];
    dates.push(`${month}/${day}`);
    groupedByYear.set(year, dates);
  });

  return [...groupedByYear.entries()]
    .map(([year, dates]) => `${year}년 ${dates.join(', ')}`)
    .join('\n');
}

function normalizeMaintenance(item) {
  const executors = splitPeople(item.mainDev);
  const inspection = formatInspectionDates(item.inspectionDates);
  const detail = splitDetailLines(item.progressIssues, item.remarks);

  return {
    ...item,
    id: `maintenance-${item.id || item.rowNo}`,
    detailId: item.id,
    workHistoryType: 'MAINTENANCE',
    no: text(item.no || item.rowNo),
    customerName: text(item.maintenanceName),
    siteCode: text(item.siteCode),
    salesRep: text(item.salesRep),
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
    row.salesRep,
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

  return words.every((word) => includesKeyword(target, word));
}

function getSearchRankValues(row) {
  return [
    row.no,
    row.customerName,
    row.siteCode,
    row.salesRep,
    row.projectType,
    ...(row.executors || []),
    row.startDate,
    row.contractEnd,
    row.latestIssue,
    row.inspection,
    ...(row.infraTypes || []),
    ...(row.detail || []),
  ]
    .map((value) => text(value).toLowerCase().replace(/\s+/g, ' ').trim())
    .filter(Boolean);
}

function getSearchMatchScore(row, keyword) {
  const terms = getKeywordTerms(keyword).map((term) => term.toLowerCase());
  if (terms.length === 0) return 0;

  const query = terms.join(' ');
  const values = getSearchRankValues(row);
  const exactQuery = values.some((value) => value === query) ? 1 : 0;
  const consecutiveQuery = values.some((value) => value.includes(query)) ? 1 : 0;
  const allTermsInOneValue = values.some((value) => terms.every((term) => includesKeyword(value, term))) ? 1 : 0;
  const exactTermCount = terms.filter((term) => values.some((value) => value === term)).length;
  const matchedValueCount = terms.reduce(
    (count, term) => count + values.filter((value) => includesKeyword(value, term)).length,
    0,
  );

  return (exactQuery * 1_000_000)
    + (consecutiveQuery * 100_000)
    + (allTermsInOneValue * 10_000)
    + (exactTermCount * 1_000)
    + matchedValueCount;
}

function includesKeywordTerms(value, terms, requireAll = true) {
  const target = text(value);

  if (!target || terms.length === 0) {
    return false;
  }

  return requireAll
    ? terms.every((term) => includesKeyword(target, term))
    : terms.some((term) => includesKeyword(target, term));
}

function getSearchPreview(row, keyword) {
  const terms = getKeywordTerms(keyword);

  if (terms.length === 0) {
    return row.latestIssue;
  }

  const candidates = [
    ...row.detail,
    row.latestIssue,
    row.customerName,
    row.siteCode,
    row.salesRep ? `영업대표: ${row.salesRep}` : '',
    row.executors.length ? `수행인원: ${row.executors.join(', ')}` : '',
    row.projectType,
    row.infraTypes.length ? `인프라: ${row.infraTypes.join(', ')}` : '',
  ].filter(Boolean);

  return candidates.find((candidate) => includesKeywordTerms(candidate, terms, true))
    || candidates.find((candidate) => includesKeywordTerms(candidate, terms, false))
    || row.latestIssue;
}

function getFilteredRows(rows, { keyword, salesRep, executor, customer, infraTypes }) {
  return rows.filter((row) => {
    const keywordMatched = matchAllKeywords(row, keyword);
    const salesRepMatched = !salesRep || row.salesRep === salesRep;
    const executorMatched = !executor || row.executors.includes(executor);
    const customerMatched = !customer || row.customerName === customer;
    const infraMatched = infraTypes.length === 0 || infraTypes.every((infra) => row.infraTypes.includes(infra));

    return keywordMatched && salesRepMatched && executorMatched && customerMatched && infraMatched;
  });
}

function WorkIssueFilterBar({
  keyword,
  setKeyword,
  salesRep,
  setSalesRep,
  executor,
  setExecutor,
  customer,
  setCustomer,
  selectedInfraTypes,
  setSelectedInfraTypes,
  salesRepOptions,
  executorOptions,
  customerOptions,
  showCustomerFilter,
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
          <LabeledInput label="검색어" compact>
            <input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="검색어 입력..."
              className={searchInputClass}
            />
          </LabeledInput>
        </div>

        <div className="w-[180px]">
          <label className="mb-1 block text-xs font-medium text-slate-700">영업대표</label>
          <select
            value={salesRep}
            onChange={(event) => setSalesRep(event.target.value)}
            className="h-9 w-full rounded-lg border border-slate-300 bg-white px-3 text-sm outline-none ring-0 transition focus:border-slate-500"
          >
            <option value="">전체 영업대표</option>
            {salesRepOptions.map((name) => (
              <option key={name} value={name}>{name}</option>
            ))}
          </select>
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
          <div className="flex flex-wrap gap-1.5">
            {INFRA_TYPES.map((infra) => {
              const selected = selectedInfraTypes.includes(infra);

              return (
                <button
                  key={infra}
                  type="button"
                  onClick={() => toggleInfra(infra)}
                  className={`inline-flex h-8 min-w-[58px] items-center justify-center rounded-md border px-2 text-xs font-bold transition ${
                    selected
                      ? 'border-slate-900 bg-slate-900 text-white shadow-sm'
                      : 'border-slate-300 bg-slate-50 text-slate-600 hover:border-slate-400 hover:bg-white hover:text-slate-900'
                  }`}
                >
                  {infra}
                </button>
              );
            })}
          </div>
        </div>

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

function parseTimelineEntries(value, fallbackExecutor, createdAt) {
  const entries = [];
  let current = null;
  const toSortKey = (label) => {
    const matched = text(label).match(/(\d{1,2})[/.](\d{1,2})/);
    if (!matched) return 0;
    return Number(matched[1]) * 100 + Number(matched[2]);
  };

  text(value)
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .forEach((line) => {
      const dateHeader = parseTimelineDateHeader(line);

      if (dateHeader) {
        if (current) entries.push(current);

        current = {
          label: dateHeader.label,
          // 기간 표기(05/20~21)는 종료일이 아닌 시작일(05/20)을 정렬 기준으로 사용합니다.
          sortKey: toSortKey(dateHeader.label),
          type: dateHeader.type,
          executor: fallbackExecutor || '',
          content: [],
        };

        if (dateHeader.content) {
          current.content.push(dateHeader.content.replace(/^[\s\-•·]+/, '').trim());
        }
        return;
      }

      if (!current) {
        current = {
          label: '',
          sortKey: 0,
          type: '',
          executor: fallbackExecutor || '',
          content: [],
        };
      }

      current.content.push(line.replace(/^[\s\-•·]+/, '').trim());
    });

  if (current) entries.push(current);

  const normalizedEntries = entries
    .map((entry) => ({
      ...entry,
      content: entry.content.filter(Boolean),
      // 프로젝트 종료가 포함된 내역은 날짜와 관계없이 항상 최상단에 표시합니다.
      projectClosed: entry.content.some((item) => /\[프로젝트\s*종료\]|\[종료\]/.test(item)),
    }))
    .filter((entry) => entry.label || entry.content.length);

  // 원문은 최신 날짜부터 작성됩니다. 아래로 내려가며 월/일이 커지면 연도가 바뀐 것으로 봅니다.
  let year = Number(text(createdAt).slice(0, 4)) || new Date().getFullYear();
  let previousMonthDay = null;

  return normalizedEntries.map((entry) => {
    if (!entry.sortKey) return entry;
    if (previousMonthDay !== null && entry.sortKey > previousMonthDay) year -= 1;
    previousMonthDay = entry.sortKey;
    return { ...entry, year, sortKey: year * 10000 + entry.sortKey };
  });
}

function DetailSectionTitle({ children, color = 'text-blue-400' }) {
  return (
    <div className={`mb-4 flex items-center gap-2 text-xs font-black ${color}`}>
      <span className="text-sm">⌁</span>
      <span>{children}</span>
    </div>
  );
}

function TimelineBox({ entries, accent = 'blue', keyword = '' }) {
  const dotClass = accent === 'teal' ? 'bg-teal-500' : 'bg-blue-500';
  const headerClass = accent === 'teal'
    ? 'bg-teal-50 text-teal-900'
    : 'bg-blue-50 text-blue-900';
  const badgeClass = accent === 'teal'
    ? 'bg-teal-100 text-teal-700'
    : 'bg-blue-100 text-blue-700';

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
      {entries.length === 0 ? (
        <div className="px-5 py-5 text-sm text-slate-500">상세 진행 내용이 없습니다.</div>
      ) : (
        entries.map((entry, entryIndex) => (
          <div key={`${entry.label}-${entryIndex}`} className="border-b border-slate-100 last:border-b-0">
            {(entry.label || entry.executor || entry.type) ? (
              <div className={`flex items-center gap-3 px-5 py-3 text-xs font-bold ${headerClass}`}>
                {entry.label ? <span className="font-mono">{highlightText(entry.label, keyword)}</span> : null}
                {entry.type ? <span className={`rounded-full px-2 py-0.5 text-[10px] ${badgeClass}`}>{highlightText(entry.type, keyword)}</span> : null}
                {entry.executor ? <span className="border-l border-slate-200 pl-3 text-slate-600">{highlightText(entry.executor, keyword)}</span> : null}
              </div>
            ) : null}
            <ul className="space-y-3 px-5 py-4 text-sm text-slate-700">
              {entry.content.map((item, itemIndex) => (
                <li key={`${entry.label}-${itemIndex}`} className="flex gap-3">
                  <span className={`mt-2 h-2 w-2 shrink-0 rounded-full ${dotClass}`} />
                  <span>{highlightText(item, keyword)}</span>
                </li>
              ))}
            </ul>
          </div>
        ))
      )}
    </div>
  );
}

function StatBox({ title, children }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 text-sm text-slate-800 shadow-sm">
      <div className="mb-2 text-[11px] font-bold text-slate-500">{title}</div>
      {children}
    </div>
  );
}

function DetailPanel({ rowId, detail, row, type, colSpan, keyword = '' }) {
  if (row) {
    const isProject = type === 'PROJECT';
    const timelineEntries = (isProject && row.projectLogSources?.length
      ? row.projectLogSources.flatMap((source) => parseTimelineEntries(source.progressLogs, source.executor, source.createdAt))
      : parseTimelineEntries(row.progressIssues, text(row.mainDev), row.createdAt))
      .sort((a, b) => Number(b.projectClosed) - Number(a.projectClosed) || b.sortKey - a.sortKey);
    const issueLines = splitDetailLines(isProject ? row.remainingIssues : row.remarks);
    const people = row.executors?.length ? row.executors : splitPeople([row.mainDev, row.subDev].filter(Boolean).join(','));
    const maintenanceOwners = splitPeople(row.mainDev);
    const projectPersonStats = new Map();

    if (isProject) {
      (row.projectLogSources || []).forEach((source) => {
        splitPeople(source.executor).forEach((person) => {
          const current = projectPersonStats.get(person) || { visits: 0, md: 0 };
          projectPersonStats.set(person, {
            visits: current.visits + number(source.visits),
            md: current.md + number(source.md),
          });
        });
      });
    }

    return (
      <tr className="bg-slate-50">
        <td colSpan={colSpan} className="p-0">
          <div className="grid gap-8 border-y border-slate-200 bg-slate-50 px-6 py-7 text-slate-800 lg:grid-cols-2">
            <div>
              <DetailSectionTitle color={isProject ? 'text-blue-700' : 'text-teal-700'}>
                {isProject ? '금주 실적 및 진행 내역 (누적)' : '유지보수 진행내역 및 이슈'}
              </DetailSectionTitle>
              <TimelineBox entries={timelineEntries} accent={isProject ? 'blue' : 'teal'} keyword={keyword} />
              {isProject && row.startDate ? (
                <div className="mt-4 inline-flex rounded-md border border-blue-200 bg-blue-50 px-3 py-1 text-xs font-bold text-blue-700">
                  Start: {row.startDate}
                </div>
              ) : null}
            </div>

            <div className="space-y-6">
              <div>
                <DetailSectionTitle color="text-amber-700">잔여 사항 및 이슈 (주의요망)</DetailSectionTitle>
                <div className="rounded-xl border border-amber-200 bg-amber-50 px-5 py-5 text-sm leading-7 text-amber-800 shadow-sm">
                  {issueLines.length ? (
                    issueLines.map((item, index) => <div key={`${row.id}-issue-${index}`}>{highlightText(item, keyword)}</div>)
                  ) : (
                    <div>잔여 사항 및 이슈가 없습니다.</div>
                  )}
                </div>
              </div>

              <div className="grid gap-4 md:grid-cols-2">
                <StatBox title={isProject ? '구축 범위' : '지원 범위'}>
                  {isProject ? (
                    <div className="whitespace-pre-wrap font-bold leading-6">
                      {highlightText(text(row.scope) || row.infraTypes?.join('\n') || '-', keyword)}
                    </div>
                  ) : (
                    <MaintenanceSupportScope
                      smsStatus={row.smsStatus}
                      nmsStatus={row.nmsStatus}
                      renderValue={(value) => highlightText(value, keyword)}
                    />
                  )}
                </StatBox>
                <StatBox title="인원별 지원 횟수 / MD">
                  <div className="space-y-2">
                    {isProject && people.length ? people.map((person) => {
                      const stats = projectPersonStats.get(person);
                      const visits = stats?.visits;
                      const md = stats?.md;

                      return (
                        <div key={person} className="flex items-center justify-between gap-4">
                          <span className="font-bold text-slate-700">{highlightText(person, keyword)}</span>
                          <span className="font-mono font-black text-slate-900">
                            {formatMetric(visits)}회&nbsp; / &nbsp;{formatMetric(md)}MD
                          </span>
                        </div>
                      );
                    }) : null}
                    {!isProject && maintenanceOwners.length ? maintenanceOwners.map((person) => (
                        <div key={person} className="flex items-center justify-between gap-4">
                          <span className="font-bold text-slate-700">{highlightText(person, keyword)}</span>
                          <span className="font-mono font-black text-slate-900">
                            {formatMetric(row.visits)}회&nbsp; / &nbsp;{formatMetric(row.md)}MD
                          </span>
                        </div>
                    )) : null}
                    {!isProject && !maintenanceOwners.length ? <div className="text-slate-500">-</div> : null}
                    {isProject && !people.length ? <div className="text-slate-500">-</div> : null}
                  </div>
                </StatBox>
              </div>
            </div>
          </div>
        </td>
      </tr>
    );
  }

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
                <span>{highlightText(item, keyword)}</span>
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

const ProjectTableRow = React.memo(function ProjectTableRow({ row, open, onToggle, keyword }) {
  return (
    <React.Fragment>
      <tr
        className="cursor-pointer transition hover:bg-slate-50"
        onClick={() => onToggle(row.id)}
      >
        <td className="px-4 py-4 font-mono text-slate-500">{row.no}</td>
        <td className="px-4 py-4">
          <div className="flex min-w-0 items-center gap-2">
            <div
              className="min-w-0 truncate font-bold text-slate-900"
              title={`${row.customerName}${row.siteCode ? `(${row.siteCode})` : ''}`}
            >
              {highlightText(`${row.customerName}${row.siteCode ? `(${row.siteCode})` : ''}`, keyword)}
            </div>
            <DetailPopupButton
              disabled={!row.detailId}
              onOpen={() => openWorkIssueHistoryDetailWindow('PROJECT', row)}
            />
          </div>
          <div className="mt-0.5 text-xs text-slate-500">{highlightText(row.projectType || '-', keyword)}</div>
        </td>
        <td className="px-4 py-4 truncate whitespace-nowrap text-slate-700" title={row.salesRep}>{highlightText(row.salesRep || '-', keyword)}</td>
        <td className="px-4 py-4 truncate whitespace-nowrap text-slate-700" title={row.executors.join(', ')}>{highlightText(row.executors.join(', ') || '-', keyword)}</td>
        <td className="px-4 py-4 whitespace-nowrap font-mono text-slate-600">{highlightText(row.startDate || '-', keyword)}</td>
        <td className="px-4 py-4 text-slate-700">{highlightText(row.latestIssue, keyword)}</td>
        <td className="px-4 py-4 text-slate-400">
          <ChevronIcon open={open} />
        </td>
      </tr>

      {open ? <DetailPanel rowId={row.id} detail={row.detail} row={row} type="PROJECT" colSpan={7} keyword={keyword} /> : null}
    </React.Fragment>
  );
});

function ProjectTable({ rows, keyword }) {
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
          <col className="w-[34%]" />
          <col className="w-[11%]" />
          <col className="w-[15%]" />
          <col className="w-[11%]" />
          <col className="w-[21%]" />
          <col className="w-[3%]" />
        </colgroup>
        <thead className="bg-slate-100 text-xs font-bold uppercase tracking-wide text-slate-500">
          <tr>
            <th className="px-4 py-3 whitespace-nowrap">NO</th>
            <th className="px-4 py-3">고객사(사업명)</th>
            <th className="px-4 py-3 whitespace-nowrap">영업대표</th>
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
              keyword={keyword}
            />
          ))}

          {rows.length === 0 ? (
            <tr>
              <td colSpan={7} className="px-4 py-16 text-center text-sm text-slate-500">
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

const MaintenanceTableRow = React.memo(function MaintenanceTableRow({ row, open, onToggle, keyword }) {
  return (
    <React.Fragment>
      <tr
        className="cursor-pointer transition hover:bg-slate-50"
        onClick={() => onToggle(row.id)}
      >
        <td className="px-4 py-4 font-mono text-slate-500">{row.no}</td>
        <td className="px-4 py-4">
          <div className="flex min-w-0 items-center gap-2">
            <div
              className="min-w-0 truncate font-bold text-slate-900"
              title={row.customerName}
            >
              {highlightText(row.customerName, keyword)}
            </div>
            <DetailPopupButton
              disabled={!row.detailId}
              onOpen={() => openWorkIssueHistoryDetailWindow('MAINTENANCE', row)}
            />
          </div>
          <div className="mt-0.5 text-xs text-slate-500">{highlightText(row.siteCode || '-', keyword)}</div>
        </td>
        <td className="px-4 py-4 truncate whitespace-nowrap text-slate-700" title={row.salesRep}>{highlightText(row.salesRep || '-', keyword)}</td>
        <td className="px-4 py-4 truncate whitespace-nowrap text-slate-700" title={maintenanceAssigneeText(row)}>{highlightText(maintenanceAssigneeText(row), keyword)}</td>
        <td className="px-4 py-4 whitespace-nowrap font-mono text-slate-600">{highlightText(row.contractEnd || '-', keyword)}</td>
        <td className="px-4 py-4 text-slate-700">{highlightText(row.latestIssue, keyword)}</td>
        <td className="whitespace-pre-line px-4 py-4 text-slate-700">{highlightText(row.inspection, keyword)}</td>
        <td className="px-4 py-4 text-slate-400">
          <ChevronIcon open={open} />
        </td>
      </tr>

      {open ? <DetailPanel rowId={row.id} detail={row.detail} row={row} type="MAINTENANCE" colSpan={8} keyword={keyword} /> : null}
    </React.Fragment>
  );
});

function MaintenanceTable({ rows, keyword }) {
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
          <col className="w-[24%]" />
          <col className="w-[11%]" />
          <col className="w-[12%]" />
          <col className="w-[12%]" />
          <col className="w-[17%]" />
          <col className="w-[19%]" />
          <col className="w-[3%]" />
        </colgroup>
        <thead className="bg-slate-100 text-xs font-bold uppercase tracking-wide text-slate-500">
          <tr>
            <th className="px-4 py-3 whitespace-nowrap">NO</th>
            <th className="px-4 py-3">유지보수명</th>
            <th className="px-4 py-3 whitespace-nowrap">영업대표</th>
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
              keyword={keyword}
            />
          ))}

          {rows.length === 0 ? (
            <tr>
              <td colSpan={8} className="px-4 py-16 text-center text-sm text-slate-500">
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
  const results = [
    ...projectRows.map((row) => ({ ...row, category: '프로젝트', workHistoryType: 'PROJECT' })),
    ...maintenanceRows.map((row) => ({ ...row, category: '유지보수', workHistoryType: 'MAINTENANCE' })),
  ]
    .map((row, index) => ({ row, index, score: getSearchMatchScore(row, keyword) }))
    .sort((left, right) => right.score - left.score || left.index - right.index)
    .map(({ row }) => row);

  return (
    <div className="space-y-3">
      {results.map((row) => (
        <div key={`${row.category}-${row.id}`} className="rounded-xl border border-slate-200 bg-white p-4">
          <div className="mb-2 flex items-center gap-2">
            <span className="rounded-full bg-blue-50 px-2 py-1 text-xs font-bold text-blue-600">{row.category}</span>
            <span
              className="min-w-0 truncate text-sm font-bold text-slate-900"
              title={`${row.customerName}${row.siteCode ? `(${row.siteCode})` : ''}`}
            >
              {highlightText(`${row.customerName}${row.siteCode ? `(${row.siteCode})` : ''}`, keyword)}
            </span>
            <DetailPopupButton
              disabled={!row.detailId}
              onOpen={() => openWorkIssueHistoryDetailWindow(row.workHistoryType, row)}
            />
          </div>
          <p className="text-sm text-slate-700">{highlightText(getSearchPreview(row, keyword), keyword)}</p>
          <p className="mt-2 text-xs text-slate-500">영업대표: {highlightText(row.salesRep || '-', keyword)}</p>
          <p className="mt-2 text-xs text-slate-500">
            수행인원: {highlightText(row.workHistoryType === 'MAINTENANCE' ? maintenanceAssigneeText(row) : row.executors.join(', ') || '-', keyword)}
          </p>
        </div>
      ))}

      {results.length === 0 ? (
        <div className="rounded-xl border border-slate-200 bg-white px-4 py-16 text-center text-sm text-slate-500">
          표시할 프로젝트 및 유지보수 항목이 없습니다.
        </div>
      ) : null}
    </div>
  );
}

export default function WorkIssueHistoryPage() {
  const fileInputRef = useRef(null);
  const initialParams = new URLSearchParams(window.location.search);

  const [activeInnerTab, setActiveInnerTab] = useState(getInitialWorkIssueTab);
  const [uploads, setUploads] = useState([]);
  const [selectedUploadId, setSelectedUploadId] = useState('');
  const [projects, setProjects] = useState([]);
  const [maintenanceRows, setMaintenanceRows] = useState([]);

  const [keyword, setKeyword] = useState(() => getInitialSearchParam('keyword'));
  const [salesRep, setSalesRep] = useState(() => getInitialSearchParam('salesRep'));
  const [executor, setExecutor] = useState(() => getInitialSearchParam('executor'));
  const [customer, setCustomer] = useState(() => getInitialSearchParam('customerName', getInitialSearchParam('customer')));
  const [selectedInfraTypes, setSelectedInfraTypes] = useState(() => {
    const infraTypes = getInitialSearchParam('infraTypes');
    if (infraTypes) {
      return infraTypes.split(',').filter(Boolean);
    }

    const infraType = getInitialSearchParam('infraType');
    return infraType ? [infraType] : [];
  });

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

      const uniqueUploads = uniqueUploadList(uploadList);
      const resolvedUploadId = uploadId || (uniqueUploads?.[0]?.uploadId ? String(uniqueUploads[0].uploadId) : '');
      const query = resolvedUploadId ? `?uploadId=${resolvedUploadId}` : '';

      const [projectData, maintenanceData] = await Promise.all([
        fetch(`${WORK_ISSUE_API}/projects${query}`).then((response) => readApiResponse(response, '프로젝트 조회에 실패했습니다.')),
        fetch(`${WORK_ISSUE_API}/maintenance${query}`).then((response) => readApiResponse(response, '유지보수 조회에 실패했습니다.')),
      ]);

      setUploads(uniqueUploads);
      setSelectedUploadId(resolvedUploadId);
      setProjects(groupProjectRows((projectData || []).map(normalizeProject)));
      setMaintenanceRows((maintenanceData || []).map(normalizeMaintenance));
    } catch (e) {
      setError(e.message || '작업 및 이슈이력 조회 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    fetchWorkIssueData(initialParams.get('uploadId') || '');
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

  const salesRepOptions = useMemo(() => {
    return [...new Set(allRows.map((row) => row.salesRep).filter(Boolean))].sort();
  }, [allRows]);

  const customerOptions = useMemo(() => {
    return [...new Set(projects.map((row) => row.customerName).filter(Boolean))].sort();
  }, [projects]);

  const filteredProjects = useMemo(() => {
    return getFilteredRows(projects, {
      keyword,
      salesRep,
      executor,
      customer,
      infraTypes: selectedInfraTypes,
    });
  }, [projects, keyword, salesRep, executor, customer, selectedInfraTypes]);

  const filteredMaintenance = useMemo(() => {
    return getFilteredRows(maintenanceRows, {
      keyword,
      salesRep,
      executor,
      customer: '',
      infraTypes: selectedInfraTypes,
    });
  }, [maintenanceRows, keyword, salesRep, executor, selectedInfraTypes]);

  const buildSavedScreenUrl = () => {
    const params = new URLSearchParams();
    params.set('menu', 'WORK_ISSUE_HISTORY');
    params.set('tab', activeInnerTab);
    params.set('innerTab', activeInnerTab);
    if (selectedUploadId) params.set('uploadId', selectedUploadId);
    if (keyword.trim()) params.set('keyword', keyword.trim());
    if (salesRep) params.set('salesRep', salesRep);
    if (executor) params.set('executor', executor);
    if (customer) params.set('customerName', customer);
    if (selectedInfraTypes.length > 0) params.set('infraTypes', selectedInfraTypes.join(','));
    if (selectedInfraTypes.length === 1) params.set('infraType', selectedInfraTypes[0]);

    return `${window.location.origin}${window.location.pathname}?${params.toString()}`;
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
                  className={`flex min-h-[42px] flex-col items-center justify-center rounded-xl border px-5 py-2 text-sm font-bold leading-tight transition ${
                    active
                      ? 'border-blue-600 bg-blue-50 text-blue-600 shadow-sm'
                      : 'border-transparent text-slate-500 hover:bg-slate-100 hover:text-slate-900'
                  }`}
                >
                  <span>{tab.label}</span>
                  {tab.subLabel ? <span className="mt-0.5 text-[11px] font-semibold">{tab.subLabel}</span> : null}
                </button>
              );
            })}

            <div className="ml-auto flex flex-wrap items-center gap-2">
              <SaveScreenButton
                title={`작업 및 이슈이력${keyword.trim() ? ` - ${keyword.trim()}` : ''}`}
                url={buildSavedScreenUrl()}
                className="w-full sm:w-[132px]"
              />

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

          {(message || error || loading) ? (
            <div className="flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-xs text-slate-600">
              {error ? <span className="font-semibold text-red-600">{error}</span> : null}
              {!error && message ? <span className="font-semibold text-blue-600">{message}</span> : null}
              {!error && !message && loading ? <span className="font-semibold text-slate-500">데이터를 조회 중입니다.</span> : null}
            </div>
          ) : null}

          <WorkIssueFilterBar
            keyword={keyword}
            setKeyword={setKeyword}
            salesRep={salesRep}
            setSalesRep={setSalesRep}
            executor={executor}
            setExecutor={setExecutor}
            customer={customer}
            setCustomer={setCustomer}
            selectedInfraTypes={selectedInfraTypes}
            setSelectedInfraTypes={setSelectedInfraTypes}
            salesRepOptions={salesRepOptions}
            executorOptions={executorOptions}
            customerOptions={customerOptions}
            showCustomerFilter={activeInnerTab === 'projects'}
          />

          {activeInnerTab === 'projects' ? <ProjectTable rows={filteredProjects} keyword={keyword} /> : null}
          {activeInnerTab === 'maintenance' ? <MaintenanceTable rows={filteredMaintenance} keyword={keyword} /> : null}
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
