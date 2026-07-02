import React, { useEffect, useRef, useState } from 'react';
import SectionCard from '../components/common/SectionCard';
import LabeledInput from '../components/common/LabeledInput';
import { API_BASE, infraOptions } from '../constants/patchHistoryOptions';

const searchInputClass =
  'h-9 w-full rounded-lg border border-slate-300 px-3 text-sm outline-none ring-0 focus:border-slate-500';

const toolbarButtonClass =
  'h-9 shrink-0 rounded-lg px-3 text-sm font-semibold shadow-sm transition';

const DEFAULT_PAGE_SIZE = 7;
const PAGE_SIZE_OPTIONS = [7, 10, 20, 50];

function toDateInputValue(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function getDefaultEndDate() {
  return toDateInputValue(new Date());
}

function formatDateTime(value) {
  if (!value) {
    return '-';
  }
  return String(value).replace('T', ' ').slice(0, 10);
}

function normalizeList(value) {
  if (!value) {
    return [];
  }
  if (Array.isArray(value)) {
    return value;
  }
  return [value];
}

function rowClass(level) {
  switch (level) {
    case 'VERY_HIGH':
      return 'bg-emerald-100/80 hover:bg-emerald-100';
    case 'HIGH':
      return 'bg-green-50 hover:bg-green-100/70';
    case 'MEDIUM':
      return 'bg-yellow-50 hover:bg-yellow-100/70';
    default:
      return 'bg-white hover:bg-slate-50';
  }
}

function buildPageItems(currentPage, totalPages) {
  if (totalPages <= 0) {
    return [];
  }

  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, index) => index);
  }

  const pages = new Set([0, totalPages - 1]);
  for (let page = currentPage - 2; page <= currentPage + 2; page += 1) {
    if (page >= 0 && page < totalPages) {
      pages.add(page);
    }
  }

  const sortedPages = Array.from(pages).sort((a, b) => a - b);
  const items = [];

  sortedPages.forEach((page, index) => {
    const previousPage = sortedPages[index - 1];
    if (index > 0 && page - previousPage > 1) {
      items.push(`ellipsis-${page}`);
    }
    items.push(page);
  });

  return items;
}

function buildSearchParams({
  keyword,
  infraType,
  customerName,
  startDate,
  endDate,
  patchHistoryPage,
  patchHistorySize,
  knowledgePage,
  knowledgeSize,
  workIssuePage,
  workIssueSize,
  workIssueType,
}) {
  const params = new URLSearchParams();

  if (keyword.trim()) {
    params.append('keyword', keyword.trim());
  }
  if (customerName.trim()) {
    params.append('customerName', customerName.trim());
  }
  if (infraType !== 'ALL') {
    params.append('infraType', infraType);
  }
  if (startDate) {
    params.append('startDate', startDate);
  }
  if (endDate) {
    params.append('endDate', endDate);
  }
  if (workIssueType !== 'ALL') {
    params.append('workIssueType', workIssueType);
  }

  params.append('patchHistoryPage', String(patchHistoryPage));
  params.append('patchHistorySize', String(patchHistorySize));
  params.append('knowledgePage', String(knowledgePage));
  params.append('knowledgeSize', String(knowledgeSize));
  params.append('workIssuePage', String(workIssuePage));
  params.append('workIssueSize', String(workIssueSize));

  return params;
}

function SummaryCard({ label, count, description }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white px-4 py-4 shadow-sm">
      <div className="text-sm text-slate-500">{label}</div>
      <div className="mt-1 text-2xl font-bold text-slate-900">{(count || 0).toLocaleString()}건</div>
      <div className="mt-1 text-xs text-slate-500">{description}</div>
    </div>
  );
}

function TruncateCell({ value, className = '', strong = false }) {
  const text = value || '-';
  return (
    <div
      title={String(text)}
      className={`min-w-0 truncate ${strong ? 'font-medium text-slate-900' : ''} ${className}`}
    >
      {text}
    </div>
  );
}

export default function GlobalSearchPage() {
  const [keyword, setKeyword] = useState('');
  const [infraType, setInfraType] = useState('ALL');
  const [customerName, setCustomerName] = useState('');
  const [workIssueType, setWorkIssueType] = useState('ALL');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState(getDefaultEndDate);

  const [patchHistoryRows, setPatchHistoryRows] = useState([]);
  const [knowledgeRows, setKnowledgeRows] = useState([]);
  const [workIssueHistoryRows, setWorkIssueHistoryRows] = useState([]);

  const [patchHistoryTotal, setPatchHistoryTotal] = useState(0);
  const [knowledgeTotal, setKnowledgeTotal] = useState(0);
  const [workIssueHistoryTotal, setWorkIssueHistoryTotal] = useState(0);
  const [workProjectTotal, setWorkProjectTotal] = useState(0);
  const [workMaintenanceTotal, setWorkMaintenanceTotal] = useState(0);
  const [totalCount, setTotalCount] = useState(0);

  const [patchHistoryPage, setPatchHistoryPage] = useState(0);
  const [patchHistorySize, setPatchHistorySize] = useState(DEFAULT_PAGE_SIZE);
  const [patchHistoryTotalPages, setPatchHistoryTotalPages] = useState(0);
  const [patchHistoryHasNext, setPatchHistoryHasNext] = useState(false);
  const [patchHistoryHasPrevious, setPatchHistoryHasPrevious] = useState(false);

  const [knowledgePage, setKnowledgePage] = useState(0);
  const [knowledgeSize, setKnowledgeSize] = useState(DEFAULT_PAGE_SIZE);
  const [knowledgeTotalPages, setKnowledgeTotalPages] = useState(0);
  const [knowledgeHasNext, setKnowledgeHasNext] = useState(false);
  const [knowledgeHasPrevious, setKnowledgeHasPrevious] = useState(false);

  const [workIssuePage, setWorkIssuePage] = useState(0);
  const [workIssueSize, setWorkIssueSize] = useState(DEFAULT_PAGE_SIZE);
  const [workIssueTotalPages, setWorkIssueTotalPages] = useState(0);
  const [workIssueHasNext, setWorkIssueHasNext] = useState(false);
  const [workIssueHasPrevious, setWorkIssueHasPrevious] = useState(false);

  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [error, setError] = useState('');

  const abortRef = useRef(null);

  const searchAll = async ({
    targetPatchHistoryPage = patchHistoryPage,
    targetPatchHistorySize = patchHistorySize,
    targetKnowledgePage = knowledgePage,
    targetKnowledgeSize = knowledgeSize,
    targetWorkIssuePage = workIssuePage,
    targetWorkIssueSize = workIssueSize,
    targetWorkIssueType = workIssueType,
  } = {}) => {
    if (abortRef.current) {
      abortRef.current.abort();
    }

    const controller = new AbortController();
    abortRef.current = controller;

    setLoading(true);
    setSearched(true);
    setError('');

    try {
      const params = buildSearchParams({
        keyword,
        infraType,
        customerName,
        startDate,
        endDate,
        patchHistoryPage: targetPatchHistoryPage,
        patchHistorySize: targetPatchHistorySize,
        knowledgePage: targetKnowledgePage,
        knowledgeSize: targetKnowledgeSize,
        workIssuePage: targetWorkIssuePage,
        workIssueSize: targetWorkIssueSize,
        workIssueType: targetWorkIssueType,
      });

      const res = await fetch(`${API_BASE}/api/global-search?${params.toString()}`, {
        signal: controller.signal,
      });
      const body = await res.json();

      if (!res.ok || !body.success) {
        throw new Error(body.message || '통합검색에 실패했습니다.');
      }

      const data = body.data || {};
      setPatchHistoryRows(data.patchHistories || []);
      setKnowledgeRows(data.knowledgeShares || []);
      setWorkIssueHistoryRows(data.workIssueHistories || []);

      setPatchHistoryTotal(data.patchHistoryTotal || 0);
      setKnowledgeTotal(data.knowledgeTotal || 0);
      setWorkIssueHistoryTotal(data.workIssueHistoryTotal || 0);
      setWorkProjectTotal(data.workProjectTotal || 0);
      setWorkMaintenanceTotal(data.workMaintenanceTotal || 0);
      setTotalCount(data.total || 0);

      setPatchHistoryPage(
        Number.isInteger(data.patchHistoryPage) ? data.patchHistoryPage : targetPatchHistoryPage
      );
      setPatchHistorySize(data.patchHistorySize || targetPatchHistorySize);
      setPatchHistoryTotalPages(data.patchHistoryTotalPages || 0);
      setPatchHistoryHasNext(Boolean(data.patchHistoryHasNext));
      setPatchHistoryHasPrevious(Boolean(data.patchHistoryHasPrevious));

      setKnowledgePage(
        Number.isInteger(data.knowledgePage) ? data.knowledgePage : targetKnowledgePage
      );
      setKnowledgeSize(data.knowledgeSize || targetKnowledgeSize);
      setKnowledgeTotalPages(data.knowledgeTotalPages || 0);
      setKnowledgeHasNext(Boolean(data.knowledgeHasNext));
      setKnowledgeHasPrevious(Boolean(data.knowledgeHasPrevious));

      setWorkIssuePage(Number.isInteger(data.workIssuePage) ? data.workIssuePage : targetWorkIssuePage);
      setWorkIssueSize(data.workIssueSize || targetWorkIssueSize);
      setWorkIssueTotalPages(data.workIssueTotalPages || 0);
      setWorkIssueHasNext(Boolean(data.workIssueHasNext));
      setWorkIssueHasPrevious(Boolean(data.workIssueHasPrevious));
    } catch (e) {
      if (e.name !== 'AbortError') {
        setError(e.message || '통합검색 중 오류가 발생했습니다.');
        setPatchHistoryRows([]);
        setKnowledgeRows([]);
        setWorkIssueHistoryRows([]);
        setPatchHistoryTotal(0);
        setKnowledgeTotal(0);
        setWorkIssueHistoryTotal(0);
        setWorkProjectTotal(0);
        setWorkMaintenanceTotal(0);
        setTotalCount(0);
        setPatchHistoryTotalPages(0);
        setKnowledgeTotalPages(0);
        setWorkIssueTotalPages(0);
        setPatchHistoryHasNext(false);
        setPatchHistoryHasPrevious(false);
        setKnowledgeHasNext(false);
        setKnowledgeHasPrevious(false);
        setWorkIssueHasNext(false);
        setWorkIssueHasPrevious(false);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    searchAll({
      targetPatchHistoryPage: 0,
      targetKnowledgePage: 0,
      targetWorkIssuePage: 0,
    });

    return () => {
      if (abortRef.current) {
        abortRef.current.abort();
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSearch = () => {
    setPatchHistoryPage(0);
    setKnowledgePage(0);
    setWorkIssuePage(0);
    searchAll({
      targetPatchHistoryPage: 0,
      targetPatchHistorySize: patchHistorySize,
      targetKnowledgePage: 0,
      targetKnowledgeSize: knowledgeSize,
      targetWorkIssuePage: 0,
      targetWorkIssueSize: workIssueSize,
      targetWorkIssueType: workIssueType,
    });
  };

  const handleEnterSearch = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleSearch();
    }
  };

  const movePatchHistoryPage = (targetPage) => {
    if (targetPage < 0 || targetPage >= patchHistoryTotalPages || targetPage === patchHistoryPage) {
      return;
    }

    searchAll({
      targetPatchHistoryPage: targetPage,
      targetPatchHistorySize: patchHistorySize,
      targetKnowledgePage: knowledgePage,
      targetKnowledgeSize: knowledgeSize,
      targetWorkIssuePage: workIssuePage,
      targetWorkIssueSize: workIssueSize,
      targetWorkIssueType: workIssueType,
    });
  };

  const moveKnowledgePage = (targetPage) => {
    if (targetPage < 0 || targetPage >= knowledgeTotalPages || targetPage === knowledgePage) {
      return;
    }

    searchAll({
      targetPatchHistoryPage: patchHistoryPage,
      targetPatchHistorySize: patchHistorySize,
      targetKnowledgePage: targetPage,
      targetKnowledgeSize: knowledgeSize,
      targetWorkIssuePage: workIssuePage,
      targetWorkIssueSize: workIssueSize,
      targetWorkIssueType: workIssueType,
    });
  };

  const changePatchHistorySize = (nextSize) => {
    setPatchHistorySize(nextSize);
    searchAll({
      targetPatchHistoryPage: 0,
      targetPatchHistorySize: nextSize,
      targetKnowledgePage: knowledgePage,
      targetKnowledgeSize: knowledgeSize,
      targetWorkIssuePage: workIssuePage,
      targetWorkIssueSize: workIssueSize,
      targetWorkIssueType: workIssueType,
    });
  };

  const changeKnowledgeSize = (nextSize) => {
    setKnowledgeSize(nextSize);
    searchAll({
      targetPatchHistoryPage: patchHistoryPage,
      targetPatchHistorySize: patchHistorySize,
      targetKnowledgePage: 0,
      targetKnowledgeSize: nextSize,
      targetWorkIssuePage: workIssuePage,
      targetWorkIssueSize: workIssueSize,
      targetWorkIssueType: workIssueType,
    });
  };

  const moveWorkIssuePage = (targetPage) => {
    if (targetPage < 0 || targetPage >= workIssueTotalPages || targetPage === workIssuePage) {
      return;
    }

    searchAll({
      targetPatchHistoryPage: patchHistoryPage,
      targetPatchHistorySize: patchHistorySize,
      targetKnowledgePage: knowledgePage,
      targetKnowledgeSize: knowledgeSize,
      targetWorkIssuePage: targetPage,
      targetWorkIssueSize: workIssueSize,
      targetWorkIssueType: workIssueType,
    });
  };

  const changeWorkIssueSize = (nextSize) => {
    setWorkIssueSize(nextSize);
    setWorkIssuePage(0);
    searchAll({
      targetPatchHistoryPage: patchHistoryPage,
      targetPatchHistorySize: patchHistorySize,
      targetKnowledgePage: knowledgePage,
      targetKnowledgeSize: knowledgeSize,
      targetWorkIssuePage: 0,
      targetWorkIssueSize: nextSize,
      targetWorkIssueType: workIssueType,
    });
  };

  const changeWorkIssueType = (nextType) => {
    if (nextType === workIssueType) {
      return;
    }

    setWorkIssueType(nextType);
    setWorkIssuePage(0);
    searchAll({
      targetPatchHistoryPage: patchHistoryPage,
      targetPatchHistorySize: patchHistorySize,
      targetKnowledgePage: knowledgePage,
      targetKnowledgeSize: knowledgeSize,
      targetWorkIssuePage: 0,
      targetWorkIssueSize: workIssueSize,
      targetWorkIssueType: nextType,
    });
  };

  const openPatchHistoryDetailWindow = (id) => {
    const url = `${window.location.origin}${window.location.pathname}?popup=patch-history-detail&id=${id}`;
    const features = 'width=1200,height=820,left=120,top=80,scrollbars=yes,resizable=yes';
    window.open(url, `patch-history-detail-${id}`, features);
  };

  const openKnowledgeDetailWindow = (id) => {
    const url = `${window.location.origin}${window.location.pathname}?popup=knowledge-detail&id=${id}`;
    const features = 'width=1200,height=820,left=120,top=80,scrollbars=yes,resizable=yes';
    window.open(url, `knowledge-detail-${id}`, features);
  };

  const openWorkIssueHistoryDetailWindow = (item) => {
    const type = item.workHistoryType === 'MAINTENANCE' ? 'MAINTENANCE' : 'PROJECT';
    const url = `${window.location.origin}${window.location.pathname}?popup=work-issue-history-detail&type=${type}&id=${item.id}`;
    const features = 'width=1200,height=820,left=120,top=80,scrollbars=yes,resizable=yes';
    window.open(url, `work-issue-history-detail-${type}-${item.id}`, features);
  };

  const currentWorkIssueTotal =
    workIssueType === 'PROJECT'
      ? workProjectTotal
      : workIssueType === 'MAINTENANCE'
      ? workMaintenanceTotal
      : workIssueHistoryTotal;

  return (
    <>
      <div className="mb-4 flex items-center gap-8">
        <h1 className="text-3xl font-bold tracking-tight text-slate-900">통합검색</h1>
        <p className="border-l border-slate-300 pl-6 text-sm text-slate-500" />
      </div>

      <div className="space-y-5">
        <SectionCard className="p-3">
          <div className="flex flex-wrap items-end gap-2 xl:flex-nowrap">
            <div className="w-full sm:w-[220px]">
              <LabeledInput label="검색어" compact>
                <input
                  className={searchInputClass}
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                  onKeyDown={handleEnterSearch}
                  placeholder="제목, 내용, 담당자, 태그"
                />
              </LabeledInput>
            </div>

            <div className="w-full sm:w-[130px]">
              <LabeledInput label="인프라" compact>
                <select
                  className={searchInputClass}
                  value={infraType}
                  onChange={(e) => setInfraType(e.target.value)}
                >
                  <option value="ALL">전체</option>
                  {infraOptions.map((option) => (
                    <option key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
              </LabeledInput>
            </div>

            <div className="w-full sm:w-[180px]">
              <LabeledInput label="고객사" compact>
                <input
                  className={searchInputClass}
                  value={customerName}
                  onChange={(e) => setCustomerName(e.target.value)}
                  onKeyDown={handleEnterSearch}
                  placeholder="고객사명"
                />
              </LabeledInput>
            </div>

            <div className="w-full sm:w-[150px]">
              <LabeledInput label="시작일" compact>
                <input
                  type="date"
                  className={searchInputClass}
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                />
              </LabeledInput>
            </div>

            <div className="w-full sm:w-[150px]">
              <LabeledInput label="종료일" compact>
                <input
                  type="date"
                  className={searchInputClass}
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
                />
              </LabeledInput>
            </div>

            <div className="flex w-full items-end sm:w-auto">
              <button
                type="button"
                onClick={handleSearch}
                disabled={loading}
                className={`${toolbarButtonClass} w-full bg-slate-900 text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400 sm:w-[88px]`}
              >
                {loading ? '검색중' : '검색'}
              </button>
            </div>
          </div>
        </SectionCard>

        {error && (
          <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <SummaryCard label="패치이력" count={patchHistoryTotal} />
          <SummaryCard label="지식공유 DB" count={knowledgeTotal} />
          <SummaryCard label="작업 및 이슈이력" count={workIssueHistoryTotal} />
          <SummaryCard label="전체 결과" count={totalCount} />
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-600 shadow-sm">
          <div className="flex flex-wrap items-center gap-4">
            <span className="font-semibold text-slate-800">일치도 안내</span>

            <span className="inline-flex items-center gap-2">
              <span className="h-3 w-6 rounded bg-emerald-100 ring-1 ring-emerald-200" />
              매우 높음
            </span>
            <span className="inline-flex items-center gap-2">
              <span className="h-3 w-6 rounded bg-green-50 ring-1 ring-green-200" />
              높음
            </span>
            <span className="inline-flex items-center gap-2">
              <span className="h-3 w-6 rounded bg-yellow-50 ring-1 ring-yellow-200" />
              보통
            </span>
            <span className="inline-flex items-center gap-2">
              <span className="h-3 w-6 rounded bg-white ring-1 ring-slate-200" />
              낮음
            </span>
          </div>
        </div>

        {!searched ? (
          <SectionCard>
            <div className="rounded-xl border border-dashed border-slate-300 px-4 py-10 text-center text-slate-500">
              전체 목록을 불러오는 중입니다.
            </div>
          </SectionCard>
        ) : (
          <div className="grid grid-cols-1 gap-5 xl:grid-cols-2 2xl:grid-cols-3">
            <SectionCard title={`패치이력 결과 (${(patchHistoryTotal || 0).toLocaleString()}건)`}>
              <div className="overflow-hidden rounded-2xl border border-slate-200">
                <table className="w-full table-fixed divide-y divide-slate-200 text-xs">
                  <thead className="bg-slate-100">
                    <tr>
                      <th className="w-[24%] whitespace-nowrap px-2 py-3 text-left font-semibold">제목</th>
                      <th className="w-[10%] whitespace-nowrap px-2 py-3 text-left font-semibold">인프라</th>
                      <th className="w-[14%] whitespace-nowrap px-2 py-3 text-left font-semibold">고객사</th>
                      <th className="w-[14%] whitespace-nowrap px-2 py-3 text-left font-semibold">등록일</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 bg-white">
                    {loading && patchHistoryRows.length === 0 ? (
                      <tr>
                        <td colSpan={4} className="px-4 py-8 text-center text-slate-500">
                          검색 중...
                        </td>
                      </tr>
                    ) : patchHistoryRows.length === 0 ? (
                      <tr>
                        <td colSpan={4} className="px-4 py-8 text-center text-slate-500">
                          검색된 패치이력이 없습니다.
                        </td>
                      </tr>
                    ) : (
                      patchHistoryRows.map((patchHistory) => (
                        <tr
                          key={patchHistory.id}
                          onClick={() => openPatchHistoryDetailWindow(patchHistory.id)}
                          className={`cursor-pointer transition ${rowClass(patchHistory.matchLevel)}`}
                        >
                          <td className="min-w-0 px-2 py-3">
                            <TruncateCell value={patchHistory.title} strong />
                          </td>
                          <td className="min-w-0 px-2 py-3">
                            <TruncateCell value={normalizeList(patchHistory.infraTypes).join(', ')} />
                          </td>
                          <td className="min-w-0 px-2 py-3">
                            <TruncateCell value={patchHistory.customerName} />
                          </td>
                          <td className="min-w-0 px-2 py-3 text-slate-700">
                            <TruncateCell value={formatDateTime(patchHistory.createdAt)} />
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>

              <PaginationBar
                page={patchHistoryPage}
                size={patchHistorySize}
                totalPages={patchHistoryTotalPages}
                totalElements={patchHistoryTotal}
                hasPrevious={patchHistoryHasPrevious}
                hasNext={patchHistoryHasNext}
                onMovePage={movePatchHistoryPage}
                onChangeSize={changePatchHistorySize}
              />
            </SectionCard>

            <SectionCard title={`지식공유 결과 (${(knowledgeTotal || 0).toLocaleString()}건)`}>
              <div className="overflow-hidden rounded-2xl border border-slate-200">
                <table className="w-full table-fixed divide-y divide-slate-200 text-xs">
                  <thead className="bg-slate-100">
                    <tr>
                      <th className="w-[17%] whitespace-nowrap px-2 py-3 text-left font-semibold">제목</th>
                      <th className="w-[30%] whitespace-nowrap px-2 py-3 text-left font-semibold">내용</th>
                      <th className="w-[10%] whitespace-nowrap px-2 py-3 text-left font-semibold">인프라</th>
                      <th className="w-[13%] whitespace-nowrap px-2 py-3 text-left font-semibold">고객사</th>
                      <th className="w-[12%] whitespace-nowrap px-2 py-3 text-left font-semibold">담당자</th>
                      <th className="w-[18%] whitespace-nowrap px-2 py-3 text-left font-semibold">등록일</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 bg-white">
                    {loading && knowledgeRows.length === 0 ? (
                      <tr>
                        <td colSpan={6} className="px-4 py-8 text-center text-slate-500">
                          검색 중...
                        </td>
                      </tr>
                    ) : knowledgeRows.length === 0 ? (
                      <tr>
                        <td colSpan={6} className="px-4 py-8 text-center text-slate-500">
                          검색된 지식공유 글이 없습니다.
                        </td>
                      </tr>
                    ) : (
                      knowledgeRows.map((item) => (
                        <tr
                          key={item.id}
                          onClick={() => openKnowledgeDetailWindow(item.id)}
                          className={`cursor-pointer transition ${rowClass(item.matchLevel)}`}
                        >
                          <td className="min-w-0 px-2 py-3">
                            <TruncateCell value={item.title} strong />
                          </td>
                          <td className="min-w-0 px-2 py-3 text-slate-700">
                            <TruncateCell value={item.summary || item.detail} />
                          </td>
                          <td className="min-w-0 px-2 py-3 text-slate-700">
                            <TruncateCell value={normalizeList(item.infraTypes).join(', ')} />
                          </td>
                          <td className="min-w-0 px-2 py-3">
                            <TruncateCell value={item.customerName} />
                          </td>
                          <td className="min-w-0 px-2 py-3">
                            <TruncateCell value={item.authorName} />
                          </td>
                          <td className="min-w-0 px-2 py-3 text-slate-700">
                            <TruncateCell value={formatDateTime(item.createdAt)} />
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>

              <PaginationBar
                page={knowledgePage}
                size={knowledgeSize}
                totalPages={knowledgeTotalPages}
                totalElements={knowledgeTotal}
                hasPrevious={knowledgeHasPrevious}
                hasNext={knowledgeHasNext}
                onMovePage={moveKnowledgePage}
                onChangeSize={changeKnowledgeSize}
              />
            </SectionCard>

            <SectionCard>
              <div className="mb-3 flex items-center gap-1.5 overflow-x-auto whitespace-nowrap">
                <h2 className="shrink-0 text-lg font-semibold text-slate-900">
                  작업 및 이슈이력 결과 ({workIssueHistoryTotal.toLocaleString()}건)
                </h2>
                <button
                  type="button"
                  onClick={() => changeWorkIssueType('ALL')}
                  className={`h-7 rounded-md border px-2 text-[15px] font-semibold leading-none whitespace-nowrap transition ${
                    workIssueType === 'ALL'
                      ? 'border-slate-900 bg-slate-900 text-white'
                      : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
                  }`}
                >
                  전체
                </button>
                <button
                  type="button"
                  onClick={() => changeWorkIssueType('PROJECT')}
                  className={`h-7 rounded-md border px-2 text-[15px] font-semibold leading-none whitespace-nowrap transition ${
                    workIssueType === 'PROJECT'
                      ? 'border-slate-900 bg-slate-900 text-white'
                      : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
                  }`}
                >
                  프로젝트
                </button>
                <button
                  type="button"
                  onClick={() => changeWorkIssueType('MAINTENANCE')}
                  className={`h-7 rounded-md border px-2 text-[15px] font-semibold leading-none whitespace-nowrap transition ${
                    workIssueType === 'MAINTENANCE'
                      ? 'border-slate-900 bg-slate-900 text-white'
                      : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
                  }`}
                >
                  유지보수
                </button>
              </div>

              <div className="overflow-hidden rounded-2xl border border-slate-200">
                <table className="w-full table-fixed divide-y divide-slate-200 text-xs">
                  <thead className="bg-slate-100">
                    <tr>
                      <th className="w-[24%] whitespace-nowrap px-2 py-3 text-left font-semibold">제목</th>
                      <th className="w-[42%] whitespace-nowrap px-2 py-3 text-left font-semibold">내용</th>
                      <th className="w-[14%] whitespace-nowrap px-2 py-3 text-left font-semibold">담당</th>
                      <th className="w-[20%] whitespace-nowrap px-2 py-3 text-left font-semibold">등록일</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 bg-white">
                    {loading && workIssueHistoryRows.length === 0 ? (
                      <tr>
                        <td colSpan={4} className="px-4 py-8 text-center text-slate-500">
                          검색 중...
                        </td>
                      </tr>
                    ) : workIssueHistoryRows.length === 0 ? (
                      <tr>
                        <td colSpan={4} className="px-4 py-8 text-center text-slate-500">
                          검색된 작업 및 이슈이력이 없습니다.
                        </td>
                      </tr>
                    ) : (
                      workIssueHistoryRows.map((item) => (
                        <tr
                          key={`${item.workHistoryType}-${item.id}`}
                          onClick={() => openWorkIssueHistoryDetailWindow(item)}
                          className={`cursor-pointer transition ${rowClass(item.matchLevel)}`}
                        >
                          <td className="min-w-0 px-2 py-3">
                            <TruncateCell value={item.title} strong />
                          </td>
                          <td className="min-w-0 px-2 py-3 text-slate-700">
                            <TruncateCell value={item.summary || item.detail} />
                          </td>
                          <td className="min-w-0 px-2 py-3">
                            <TruncateCell value={item.authorName} />
                          </td>
                          <td className="min-w-0 px-2 py-3 text-slate-700">
                            <TruncateCell value={formatDateTime(item.createdAt)} />
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>

              <PaginationBar
                page={workIssuePage}
                size={workIssueSize}
                totalPages={workIssueTotalPages}
                totalElements={currentWorkIssueTotal}
                hasPrevious={workIssueHasPrevious}
                hasNext={workIssueHasNext}
                onMovePage={moveWorkIssuePage}
                onChangeSize={changeWorkIssueSize}
              />
            </SectionCard>
          </div>
        )}
      </div>
    </>
  );
}

function PaginationBar({
  page,
  size,
  totalPages,
  totalElements,
  hasPrevious,
  hasNext,
  onMovePage,
  onChangeSize,
}) {
  const safeTotalPages = Math.max(totalPages || 0, 1);
  const pageItems = buildPageItems(page, safeTotalPages);

  return (
    <div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500">
      <div className="flex items-center gap-2">
        <span className="truncate">전체 {totalElements.toLocaleString()}건</span>
        <label className="flex items-center gap-1">
          <span>표시</span>
          <select
            value={size}
            onChange={(e) => onChangeSize(Number(e.target.value))}
            className="h-7 rounded border border-slate-300 bg-white px-2 text-xs"
          >
            {PAGE_SIZE_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="flex items-center gap-1">
        <button
          onClick={() => onMovePage(0)}
          disabled={!hasPrevious}
          className="rounded border border-slate-300 px-2 py-1 disabled:opacity-40"
        >
          &lt;&lt;
        </button>
        <button
          onClick={() => onMovePage(page - 1)}
          disabled={!hasPrevious}
          className="rounded border border-slate-300 px-2 py-1 disabled:opacity-40"
        >
          &lt;
        </button>

        {pageItems[0] > 0 && <span>...</span>}
        {pageItems.map((item) =>
          typeof item === 'string' ? (
            <span key={item}>...</span>
          ) : (
            <button
              key={item}
              onClick={() => onMovePage(item)}
              className={`rounded border px-2 py-1 ${
                item === page ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-300'
              }`}
            >
              {item + 1}
            </button>
          )
        )}
        {pageItems[pageItems.length - 1] < safeTotalPages - 1 && <span>...</span>}

        <button
          onClick={() => onMovePage(page + 1)}
          disabled={!hasNext}
          className="rounded border border-slate-300 px-2 py-1 disabled:opacity-40"
        >
          &gt;
        </button>
        <button
          onClick={() => onMovePage(safeTotalPages - 1)}
          disabled={!hasNext}
          className="rounded border border-slate-300 px-2 py-1 disabled:opacity-40"
        >
          &gt;&gt;
        </button>
      </div>
    </div>
  );
}
