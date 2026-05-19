import React, { useEffect, useRef, useState } from 'react';
import SectionCard from '../components/common/SectionCard';
import LabeledInput from '../components/common/LabeledInput';
import { API_BASE, infraOptions } from '../constants/issueOptions';
import PageTitle from '../components/common/PageTitle';

const searchInputClass =
  'h-9 w-full rounded-lg border border-slate-300 px-3 text-sm outline-none ring-0 focus:border-slate-500';

const toolbarButtonClass =
  'h-9 shrink-0 rounded-lg px-3 text-sm font-semibold shadow-sm transition';

// 통합검색 목록은 더 이상 최대 50건 고정 표시가 아니라 페이지 단위로 조회한다.
const DEFAULT_PAGE_SIZE = 7;
const PAGE_SIZE_OPTIONS = [7, 10, 20, 50];

function toDateInputValue(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}

function getDefaultStartDate() {
  const date = new Date();
  date.setMonth(date.getMonth() - 1);

  return toDateInputValue(date);
}

function getDefaultEndDate() {
  return toDateInputValue(new Date());
}

function formatDateTime(value) {
  if (!value) {
    return '-';
  }

  return String(value).replace('T', ' ').slice(0, 16);
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

// 페이지 버튼 목록을 만든다.
// 전체 페이지가 많을 때는 처음/마지막/현재 주변만 보여주고 중간은 ... 처리한다.
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
  issuePage,
  issueSize,
  knowledgePage,
  knowledgeSize,
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

  // 이슈와 지식공유를 각각 독립적으로 페이징한다.
  params.append('issuePage', String(issuePage));
  params.append('issueSize', String(issueSize));
  params.append('knowledgePage', String(knowledgePage));
  params.append('knowledgeSize', String(knowledgeSize));

  return params;
}

function SummaryCard({ label, count, description }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white px-4 py-4 shadow-sm">
      <div className="text-sm text-slate-500">{label}</div>
      <div className="mt-1 text-2xl font-bold text-slate-900">
        {(count || 0).toLocaleString()}건
      </div>
      <div className="mt-1 text-xs text-slate-500">{description}</div>
    </div>
  );
}

// 한 줄 말줄임 표시용 셀이다.
// title 속성을 넣어 마우스오버 시 전체 내용을 브라우저 기본 툴팁으로 확인할 수 있게 한다.
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
  const pageItems = buildPageItems(page, totalPages);

  return (
    <div className="mt-4 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
      <div className="flex flex-wrap items-center gap-3 text-xs text-slate-500">
        <span>
          전체 {totalElements.toLocaleString()}건 / {totalPages === 0 ? 0 : page + 1}페이지 / {totalPages}페이지
        </span>

        <label className="flex items-center gap-2">
          <span>표시 개수</span>
          <select
            value={size}
            onChange={(e) => onChangeSize(Number(e.target.value))}
            className="h-8 rounded-lg border border-slate-300 bg-white px-2 text-xs text-slate-700 outline-none focus:border-slate-500"
          >
            {PAGE_SIZE_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}개
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="flex flex-wrap items-center gap-1 lg:justify-end">
        <button
          type="button"
          onClick={() => onMovePage(0)}
          disabled={!hasPrevious}
          className="h-8 rounded-lg border border-slate-300 bg-white px-2 text-xs text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          처음
        </button>

        <button
          type="button"
          onClick={() => onMovePage(page - 1)}
          disabled={!hasPrevious}
          className="h-8 rounded-lg border border-slate-300 bg-white px-2 text-xs text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          이전
        </button>

        {pageItems.map((item) => (
          typeof item === 'string' ? (
            <span key={item} className="px-1 text-xs text-slate-400">
              ...
            </span>
          ) : (
            <button
              key={item}
              type="button"
              onClick={() => onMovePage(item)}
              className={`h-8 min-w-8 rounded-lg border px-2 text-xs ${
                item === page
                  ? 'border-slate-900 bg-slate-900 text-white'
                  : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
              }`}
            >
              {item + 1}
            </button>
          )
        ))}

        <button
          type="button"
          onClick={() => onMovePage(page + 1)}
          disabled={!hasNext}
          className="h-8 rounded-lg border border-slate-300 bg-white px-2 text-xs text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          다음
        </button>

        <button
          type="button"
          onClick={() => onMovePage(totalPages - 1)}
          disabled={!hasNext}
          className="h-8 rounded-lg border border-slate-300 bg-white px-2 text-xs text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          마지막
        </button>
      </div>
    </div>
  );
}

export default function GlobalSearchPage() {
  const [keyword, setKeyword] = useState('');
  const [infraType, setInfraType] = useState('ALL');
  const [customerName, setCustomerName] = useState('');
  const [startDate, setStartDate] = useState(getDefaultStartDate);
  const [endDate, setEndDate] = useState(getDefaultEndDate);

  const [issueRows, setIssueRows] = useState([]);
  const [knowledgeRows, setKnowledgeRows] = useState([]);
  const [issueTotal, setIssueTotal] = useState(0);
  const [knowledgeTotal, setKnowledgeTotal] = useState(0);
  const [totalCount, setTotalCount] = useState(0);

  const [issuePage, setIssuePage] = useState(0);
  const [issueSize, setIssueSize] = useState(DEFAULT_PAGE_SIZE);
  const [issueTotalPages, setIssueTotalPages] = useState(0);
  const [issueHasNext, setIssueHasNext] = useState(false);
  const [issueHasPrevious, setIssueHasPrevious] = useState(false);

  const [knowledgePage, setKnowledgePage] = useState(0);
  const [knowledgeSize, setKnowledgeSize] = useState(DEFAULT_PAGE_SIZE);
  const [knowledgeTotalPages, setKnowledgeTotalPages] = useState(0);
  const [knowledgeHasNext, setKnowledgeHasNext] = useState(false);
  const [knowledgeHasPrevious, setKnowledgeHasPrevious] = useState(false);

  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [error, setError] = useState('');

  const abortRef = useRef(null);

  const searchAll = async ({
    targetIssuePage = issuePage,
    targetIssueSize = issueSize,
    targetKnowledgePage = knowledgePage,
    targetKnowledgeSize = knowledgeSize,
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
        issuePage: targetIssuePage,
        issueSize: targetIssueSize,
        knowledgePage: targetKnowledgePage,
        knowledgeSize: targetKnowledgeSize,
      });

      const res = await fetch(`${API_BASE}/api/global-search?${params.toString()}`, {
        signal: controller.signal,
      });

      const body = await res.json();

      if (!res.ok || !body.success) {
        throw new Error(body.message || '통합검색에 실패했습니다.');
      }

      const data = body.data || {};

      setIssueRows(data.issues || []);
      setKnowledgeRows(data.knowledgeShares || []);
      setIssueTotal(data.issueTotal || 0);
      setKnowledgeTotal(data.knowledgeTotal || 0);
      setTotalCount(data.total || 0);

      setIssuePage(data.issuePage || 0);
      setIssueSize(data.issueSize || targetIssueSize);
      setIssueTotalPages(data.issueTotalPages || 0);
      setIssueHasNext(Boolean(data.issueHasNext));
      setIssueHasPrevious(Boolean(data.issueHasPrevious));

      setKnowledgePage(data.knowledgePage || 0);
      setKnowledgeSize(data.knowledgeSize || targetKnowledgeSize);
      setKnowledgeTotalPages(data.knowledgeTotalPages || 0);
      setKnowledgeHasNext(Boolean(data.knowledgeHasNext));
      setKnowledgeHasPrevious(Boolean(data.knowledgeHasPrevious));
    } catch (e) {
      if (e.name !== 'AbortError') {
        setError(e.message || '통합검색 중 오류가 발생했습니다.');
        setIssueRows([]);
        setKnowledgeRows([]);
        setIssueTotal(0);
        setKnowledgeTotal(0);
        setTotalCount(0);
        setIssueTotalPages(0);
        setKnowledgeTotalPages(0);
      }
    } finally {
      setLoading(false);
    }
  };

  // 최초 진입 시에도 검색을 실행한다.
  // 검색어가 비어 있으면 기본 기간/필터 기준으로 이슈와 지식공유 전체 목록을 보여준다.
  useEffect(() => {
    searchAll({
      targetIssuePage: 0,
      targetKnowledgePage: 0,
    });

    return () => {
      if (abortRef.current) {
        abortRef.current.abort();
      }
    };
    // 최초 진입 시 1회만 실행한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSearch = () => {
    setIssuePage(0);
    setKnowledgePage(0);

    searchAll({
      targetIssuePage: 0,
      targetIssueSize: issueSize,
      targetKnowledgePage: 0,
      targetKnowledgeSize: knowledgeSize,
    });
  };

  const handleEnterSearch = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleSearch();
    }
  };

  const moveIssuePage = (targetPage) => {
    if (targetPage < 0 || targetPage >= issueTotalPages || targetPage === issuePage) {
      return;
    }

    searchAll({
      targetIssuePage: targetPage,
      targetIssueSize: issueSize,
      targetKnowledgePage: knowledgePage,
      targetKnowledgeSize: knowledgeSize,
    });
  };

  const moveKnowledgePage = (targetPage) => {
    if (targetPage < 0 || targetPage >= knowledgeTotalPages || targetPage === knowledgePage) {
      return;
    }

    searchAll({
      targetIssuePage: issuePage,
      targetIssueSize: issueSize,
      targetKnowledgePage: targetPage,
      targetKnowledgeSize: knowledgeSize,
    });
  };

  const changeIssueSize = (nextSize) => {
    setIssueSize(nextSize);

    searchAll({
      targetIssuePage: 0,
      targetIssueSize: nextSize,
      targetKnowledgePage: knowledgePage,
      targetKnowledgeSize: knowledgeSize,
    });
  };

  const changeKnowledgeSize = (nextSize) => {
    setKnowledgeSize(nextSize);

    searchAll({
      targetIssuePage: issuePage,
      targetIssueSize: issueSize,
      targetKnowledgePage: 0,
      targetKnowledgeSize: nextSize,
    });
  };

  const openIssueDetailWindow = (id) => {
    const url = `${window.location.origin}${window.location.pathname}?popup=issue-detail&id=${id}`;
    const features = 'width=1200,height=820,left=120,top=80,scrollbars=yes,resizable=yes';

    window.open(url, `issue-detail-${id}`, features);
  };

  const openKnowledgeDetailWindow = (id) => {
    const url = `${window.location.origin}${window.location.pathname}?popup=knowledge-detail&id=${id}`;
    const features = 'width=1200,height=820,left=120,top=80,scrollbars=yes,resizable=yes';

    window.open(url, `knowledge-detail-${id}`, features);
  };

  return (
    <>
      <PageTitle
        title="통합검색"
      />

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
                  placeholder="제목, 증상, 내용, 태그"
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

        <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
          <SummaryCard
            label="이슈관리 시스템"
            count={issueTotal}
          />
          <SummaryCard
            label="지식공유 DB"
            count={knowledgeTotal}
          />
          <SummaryCard
            label="전체 결과"
            count={totalCount}
          />
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
          <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
            <SectionCard
              title={`이슈 결과 (${(issueTotal || 0).toLocaleString()}건)`}
            >
              <div className="overflow-hidden rounded-2xl border border-slate-200">
                <table className="w-full table-fixed divide-y divide-slate-200 text-xs">
                  <thead className="bg-slate-100">
                    <tr>
                      <th className="w-[17%] whitespace-nowrap px-2 py-3 text-left font-semibold">제목</th>
                      <th className="w-[24%] whitespace-nowrap px-2 py-3 text-left font-semibold">증상 요약</th>
                      <th className="w-[25%] whitespace-nowrap px-2 py-3 text-left font-semibold">증상 상세</th>
                      <th className="w-[8%] whitespace-nowrap px-2 py-3 text-left font-semibold">인프라</th>
                      <th className="w-[12%] whitespace-nowrap px-2 py-3 text-left font-semibold">고객사</th>
                      <th className="w-[14%] whitespace-nowrap px-2 py-3 text-left font-semibold">등록일</th>
                    </tr>
                  </thead>

                  <tbody className="divide-y divide-slate-100 bg-white">
                    {loading ? (
                      <tr>
                        <td colSpan={6} className="px-4 py-8 text-center text-slate-500">
                          검색 중...
                        </td>
                      </tr>
                    ) : issueRows.length === 0 ? (
                      <tr>
                        <td colSpan={6} className="px-4 py-8 text-center text-slate-500">
                          검색된 이슈가 없습니다.
                        </td>
                      </tr>
                    ) : (
                      issueRows.map((issue) => (
                        <tr
                          key={issue.id}
                          onClick={() => openIssueDetailWindow(issue.id)}
                          className={`cursor-pointer transition ${rowClass(issue.matchLevel)}`}
                        >
                          <td className="min-w-0 px-2 py-3">
                            <TruncateCell value={issue.title} strong />
                          </td>

                          <td className="min-w-0 px-2 py-3 text-slate-700">
                            <TruncateCell value={issue.summary} />
                          </td>

                          <td className="min-w-0 px-2 py-3 text-slate-700">
                            <TruncateCell value={issue.detail} />
                          </td>

                          <td className="min-w-0 px-2 py-3">
                            <TruncateCell value={normalizeList(issue.infraTypes).join(', ')} />
                          </td>

                          <td className="min-w-0 px-2 py-3">
                            <TruncateCell value={issue.customerName} />
                          </td>

                          <td className="min-w-0 px-2 py-3 text-slate-700">
                            <TruncateCell value={formatDateTime(issue.createdAt)} />
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>

              <PaginationBar
                page={issuePage}
                size={issueSize}
                totalPages={issueTotalPages}
                totalElements={issueTotal}
                hasPrevious={issueHasPrevious}
                hasNext={issueHasNext}
                onMovePage={moveIssuePage}
                onChangeSize={changeIssueSize}
              />
            </SectionCard>

            <SectionCard
              title={`지식공유 결과 (${(knowledgeTotal || 0).toLocaleString()}건)`}
            >
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
                    {loading ? (
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
          </div>
        )}
      </div>
    </>
  );
}