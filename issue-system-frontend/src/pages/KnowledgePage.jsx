import React, { useEffect, useMemo, useRef, useState } from 'react';
import LabeledInput from '../components/common/LabeledInput';
import SectionCard from '../components/common/SectionCard';
import CreateKnowledgeModal from '../components/modal/CreateKnowledgeModal';
import PageTitle from '../components/common/PageTitle';

const API_BASE =
    process.env.NODE_ENV === 'development'
        ? 'http://localhost:8080'
        : '';

// 지식공유 인프라 검색/등록 옵션
const infraOptions = [
  'EMS',
  'GPM',
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
  'K8S',
  'TRMS',
  'NPM',
  'BRMS',
];

// 페이지당 표시 개수 옵션
const sizeOptions = [5, 10, 20, 50];

// 패치이력 검색 영역과 동일한 input 스타일
const searchInputClass =
    'h-9 w-full rounded-lg border border-slate-300 px-3 text-sm outline-none ring-0 focus:border-slate-500';

// 패치이력 검색 영역과 동일한 button 스타일
const toolbarButtonClass =
    'h-9 shrink-0 rounded-lg px-3 text-sm font-semibold shadow-sm transition';

const AUTO_SEARCH_DELAY_MS = 350;

// input[type="date"]에 들어갈 yyyy-MM-dd 문자열 생성
function toDateInputValue(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}

// 기본 종료일: 현재 날짜
function getDefaultEndDate() {
  return toDateInputValue(new Date());
}

// 등록일 표시 포맷
function formatDateTime(value) {
  if (!value) {
    return '-';
  }

  return String(value).replace('T', ' ').slice(0, 16);
}

// 페이지 번호 목록 생성
// 전체 페이지가 많으면 처음/마지막/현재 주변만 표시하고 중간은 ... 처리
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

function stripHtml(value) {
  if (!value) {
    return '';
  }

  return String(value)
      .replace(/<img\b[^>]*>/gi, ' [이미지] ')
      .replace(/<[^>]*>/g, ' ')
      .replace(/&nbsp;/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
}

function toggleSelectedValue(values, value) {
  return values.includes(value)
      ? values.filter((item) => item !== value)
      : [...values, value];
}

function getInitialSearchParam(name, fallback = '') {
  const params = new URLSearchParams(window.location.search);
  return params.get(name) || fallback;
}

function isFromGlobalSearch() {
  const params = new URLSearchParams(window.location.search);
  return params.get('fromGlobalSearch') === '1';
}

export default function KnowledgePage() {
  // 검색 조건 상태
  // const [keyword, setKeyword] = useState('');
  // const [customerName, setCustomerName] = useState('');
  const [selectedInfraTypes, setSelectedInfraTypes] = useState([]);
  // const [startDate, setStartDate] = useState('');
  // const [endDate, setEndDate] = useState(getDefaultEndDate);
  const [keyword, setKeyword] = useState(() => getInitialSearchParam('keyword'));
  const [customerName, setCustomerName] = useState(() => getInitialSearchParam('customerName'));
  const [infraType, setInfraType] = useState(() => getInitialSearchParam('infraType', 'ALL'));
  const [startDate, setStartDate] = useState(() => getInitialSearchParam('startDate'));
  const [endDate, setEndDate] = useState(() =>
      getInitialSearchParam('endDate', isFromGlobalSearch() ? '' : getDefaultEndDate())
  );

  // 목록 상태
  const [items, setItems] = useState([]);

  // 페이징 상태
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [hasPrevious, setHasPrevious] = useState(false);
  const [hasNext, setHasNext] = useState(false);

  // 로딩/저장 상태
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  // 메시지 상태
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  // 등록 모달 상태
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);

  const pageItems = useMemo(
      () => buildPageItems(page, totalPages),
      [page, totalPages]
  );
  const autoSearchInitializedRef = useRef(false);

  // 지식공유 목록 조회
  const fetchKnowledge = async (targetPage = page, targetSize = size) => {
    setLoading(true);
    setError('');

    try {
      const params = new URLSearchParams();

      if (keyword.trim()) {
        params.append('keyword', keyword.trim());
      }

      if (customerName.trim()) {
        params.append('customerName', customerName.trim());
      }

      selectedInfraTypes.forEach((infraType) => {
        params.append('infraTypes', infraType);
      });

      if (startDate) {
        params.append('startDate', startDate);
      }

      if (endDate) {
        params.append('endDate', endDate);
      }

      params.append('page', String(targetPage));
      params.append('size', String(targetSize));

      const res = await fetch(
          `${API_BASE}/api/knowledge-shares/search?${params.toString()}`
      );

      const result = await res.json();

      if (!res.ok || !result.success) {
        throw new Error(result.message || '지식공유 목록 조회에 실패했습니다.');
      }

      const pageData = result.data;

      setItems(pageData.content || []);
      setPage(pageData.page || 0);
      setSize(pageData.size || targetSize);
      setTotalPages(pageData.totalPages || 0);
      setTotalElements(pageData.totalElements || 0);
      setHasPrevious(Boolean(pageData.hasPrevious));
      setHasNext(Boolean(pageData.hasNext));
    } catch (e) {
      setError(e.message || '지식공유 목록 조회 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  // 최초 진입 시 1회 조회
  useEffect(() => {
    fetchKnowledge(0, size);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!autoSearchInitializedRef.current) {
      autoSearchInitializedRef.current = true;
      return undefined;
    }

    const timer = setTimeout(() => {
      fetchKnowledge(0, size);
    }, AUTO_SEARCH_DELAY_MS);

    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword, customerName, selectedInfraTypes, startDate, endDate]);

  // 페이지당 표시 개수 변경
  const handleSizeChange = (e) => {
    const nextSize = Number(e.target.value);

    setSize(nextSize);
    fetchKnowledge(0, nextSize);
  };

  // 페이지 이동
  const movePage = (targetPage) => {
    if (targetPage < 0 || targetPage >= totalPages) {
      return;
    }

    fetchKnowledge(targetPage, size);
  };

  // 지식공유 등록 처리
  const handleCreate = async (payload) => {
    setSaving(true);
    setError('');
    setMessage('');

    try {
      const formData = new FormData();

      const request = {
        title: payload.title,
        customerName: payload.customerName,
        authorName: payload.authorName,

        // 실제 첨부파일 정보는 knowledge_share_attachment 테이블에 저장
        // attachmentName 컬럼은 길이 제한이 있으므로 빈 값 처리
        attachmentName: '',

        content: payload.content,
        infraTypes: payload.infraTypes,
      };

      // Spring @RequestPart("request")가 JSON 파트로 인식하도록 File 사용
      formData.append(
          'request',
          new File([JSON.stringify(request)], 'request.json', {
            type: 'application/json',
          })
      );

      // 여러 첨부파일은 같은 key(files)로 반복 append
      (payload.files || []).forEach((file) => {
        formData.append('files', file);
      });

      const res = await fetch(`${API_BASE}/api/knowledge-shares`, {
        method: 'POST',
        body: formData,
      });

      const text = await res.text();

      let result;
      try {
        result = text ? JSON.parse(text) : null;
      } catch (parseError) {
        throw new Error(text || '서버 응답을 JSON으로 해석할 수 없습니다.');
      }

      if (!res.ok || !result?.success) {
        throw new Error(result?.message || '지식공유 등록에 실패했습니다.');
      }

      setMessage(`지식공유 등록 완료 (ID: ${result.data})`);
      setIsCreateModalOpen(false);

      // 등록 후 첫 페이지부터 다시 조회
      await fetchKnowledge(0, size);
    } catch (e) {
      setError(e.message || '지식공유 등록 중 오류가 발생했습니다.');
    } finally {
      setSaving(false);
    }
  };

  // 첨부파일 다운로드 URL
  const downloadUrl = (attachmentId) => {
    return `${API_BASE}/api/knowledge-shares/attachments/${attachmentId}/download`;
  };

  // 지식공유 상세보기를 별도 브라우저 창으로 엽니다.
  const openKnowledgeDetailWindow = (id) => {
    const url = `${window.location.origin}${window.location.pathname}?popup=knowledge-detail&id=${id}`;
    const features = 'width=1200,height=820,left=120,top=80,scrollbars=yes,resizable=yes';

    window.open(url, `knowledge-detail-${id}`, features);
  };

  return (
      <>
        <PageTitle
          title="지식공유 DB"
          description="운영 지식, 장애 처리 방법, 점검 절차를 등록하고 검색합니다."
        />

        {(message || error) && (
            <div
                className={`mb-4 rounded-xl border px-4 py-3 text-sm ${
                    error
                        ? 'border-red-200 bg-red-50 text-red-700'
                        : 'border-emerald-200 bg-emerald-50 text-emerald-700'
                }`}
            >
              {error || message}
            </div>
        )}

        <div className="space-y-5">
          {/* 검색 영역: 패치이력과 같은 크기감으로 맞춤 */}
          <SectionCard className="p-3">
            <div className="flex flex-wrap items-end gap-2">
              <div className="w-full sm:w-[170px]">
                <LabeledInput label="검색어" compact>
                  <input
                      className={searchInputClass}
                      value={keyword}
                      onChange={(e) => setKeyword(e.target.value)}
                      placeholder="제목, 내용 검색"
                  />
                </LabeledInput>
              </div>

              <div className="w-full sm:w-[150px]">
                <LabeledInput label="고객사" compact>
                  <input
                      className={searchInputClass}
                      value={customerName}
                      onChange={(e) => setCustomerName(e.target.value)}
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

              {/* 등록 버튼: 우측 끝 */}
              <div className="ml-auto flex w-full justify-end sm:w-auto">
                <button
                    type="button"
                    onClick={() => setIsCreateModalOpen(true)}
                    className={`${toolbarButtonClass} w-full bg-slate-900 text-white hover:bg-slate-800 sm:w-[88px]`}
                >
                  등록
                </button>
              </div>
            </div>

            <div className="mt-3 border-t border-slate-200 pt-3">
              <div className="mb-2 text-sm font-medium text-slate-700">인프라 필터</div>
              <div className="flex flex-wrap gap-1.5">
                {infraOptions.map((option) => {
                  const selected = selectedInfraTypes.includes(option);

                  return (
                      <button
                          key={option}
                          type="button"
                          onClick={() => setSelectedInfraTypes((prev) => toggleSelectedValue(prev, option))}
                          className={`inline-flex h-8 min-w-[58px] items-center justify-center rounded-md border px-2 text-xs font-bold transition ${
                              selected
                                  ? 'border-slate-900 bg-slate-900 text-white shadow-sm'
                                  : 'border-slate-300 bg-slate-50 text-slate-600 hover:border-slate-400 hover:bg-white hover:text-slate-900'
                          }`}
                      >
                        {option}
                      </button>
                  );
                })}
              </div>
            </div>
          </SectionCard>

          {/* 목록 영역: 패치이력과 같은 카드 구조로 맞춤 */}
          <SectionCard
              title="지식 목록"
              description="제목과 내용, 인프라, 고객사 기준으로 조회합니다."
          >
            <div className="overflow-hidden rounded-2xl border border-slate-200">
              <div className="overflow-x-auto">
                <table className="w-full min-w-[1200px] divide-y divide-slate-200 text-sm">
                  <thead className="bg-slate-100">
                    <tr>
                      {/* 제목 다음에 내용이 바로 오도록 컬럼 순서 변경 */}
                      <th className="w-[15%] px-4 py-3 text-left font-semibold">
                        제목
                      </th>
                      <th className="w-[28%] px-4 py-3 text-left font-semibold">
                        내용
                      </th>
                      <th className="w-[9%] px-4 py-3 text-left font-semibold">
                        인프라
                      </th>
                      <th className="w-[10%] px-4 py-3 text-left font-semibold">
                        고객사
                      </th>
                      <th className="w-[9%] px-4 py-3 text-left font-semibold">
                        담당자
                      </th>
                      <th className="w-[12%] px-4 py-3 text-left font-semibold">
                        등록일
                      </th>
                      <th className="w-[17%] px-4 py-3 text-left font-semibold">
                        첨부파일
                      </th>
                    </tr>
                  </thead>

                  <tbody className="divide-y divide-slate-100 bg-white">
                    {loading ? (
                      <tr>
                        {/* 컬럼이 7개이므로 colSpan도 7 */}
                        <td
                          colSpan={7}
                          className="px-4 py-8 text-center text-slate-500"
                        >
                          불러오는 중...
                        </td>
                      </tr>
                    ) : items.length === 0 ? (
                      <tr>
                        {/* 컬럼이 7개이므로 colSpan도 7 */}
                        <td
                          colSpan={7}
                          className="px-4 py-8 text-center text-slate-500"
                        >
                          등록된 지식공유 글이 없습니다.
                        </td>
                      </tr>
                    ) : (
                      items.map((item) => (
                        <tr
                          key={item.id}
                          onClick={() => openKnowledgeDetailWindow(item.id)}
                          className="cursor-pointer transition hover:bg-slate-50"
                        >
                          {/* 제목 */}
                          <td className="px-4 py-3">
                            <div className="max-w-[240px] truncate font-medium text-slate-900">
                              {item.title || '-'}
                            </div>
                          </td>

                          {/* 내용: 제목 바로 다음에 표시 */}
                          <td className="px-4 py-3 text-slate-600">
                            <div className="max-w-[460px] truncate">
                              {stripHtml(item.content) || '-'}
                            </div>
                          </td>

                          {/* 인프라 */}
                          <td className="px-4 py-3 text-slate-700">
                            {(item.infraTypes || []).join(', ') || '-'}
                          </td>

                          {/* 고객사 */}
                          <td className="px-4 py-3 text-slate-700">
                            {item.customerName || '-'}
                          </td>

                          {/* 담당자 */}
                          <td className="px-4 py-3 text-slate-700">
                            {item.authorName || '-'}
                          </td>

                          {/* 등록일 */}
                          <td className="whitespace-nowrap px-4 py-3 text-slate-700">
                            {formatDateTime(item.createdAt)}
                          </td>

                          {/* 첨부파일 */}
                          <td className="px-4 py-3">
                            {item.attachments && item.attachments.length > 0 ? (
                              <div className="space-y-1">
                                {item.attachments.map((file) => (
                                  <a
                                    key={file.id}
                                    href={downloadUrl(file.id)}
                                    onClick={(e) => e.stopPropagation()}
                                    className="block max-w-[220px] truncate text-sm font-medium text-slate-700 underline underline-offset-2 hover:text-slate-900"
                                  >
                                    {file.originalFileName}
                                  </a>
                                ))}
                              </div>
                            ) : (
                              <span className="text-slate-400">-</span>
                            )}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            {/* 페이징 영역 */}
            <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex flex-wrap items-center gap-3 text-sm text-slate-500">
              <span>
                전체 {totalElements}건 / {page + 1}페이지 / {totalPages}페이지
              </span>

                <div className="flex items-center gap-2">
                  <span>표시 개수</span>
                  <select
                      value={size}
                      onChange={handleSizeChange}
                      className="h-8 rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-700 outline-none focus:border-slate-500"
                  >
                    {sizeOptions.map((option) => (
                        <option key={option} value={option}>
                          {option}개
                        </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                <button
                    type="button"
                    onClick={() => movePage(0)}
                    disabled={!hasPrevious}
                    className="h-8 rounded-lg border border-slate-300 bg-white px-2.5 text-sm text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  처음
                </button>

                <button
                    type="button"
                    onClick={() => movePage(page - 1)}
                    disabled={!hasPrevious}
                    className="h-8 rounded-lg border border-slate-300 bg-white px-2.5 text-sm text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  이전
                </button>

                {pageItems.map((item) =>
                    typeof item === 'string' ? (
                        <span key={item} className="px-2 text-sm text-slate-400">
                    ...
                  </span>
                    ) : (
                        <button
                            key={item}
                            type="button"
                            onClick={() => movePage(item)}
                            className={`h-8 min-w-8 rounded-lg border px-2 text-sm ${
                                item === page
                                    ? 'border-slate-900 bg-slate-900 text-white'
                                    : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
                            }`}
                        >
                          {item + 1}
                        </button>
                    )
                )}

                <button
                    type="button"
                    onClick={() => movePage(page + 1)}
                    disabled={!hasNext}
                    className="h-8 rounded-lg border border-slate-300 bg-white px-2.5 text-sm text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  다음
                </button>

                <button
                    type="button"
                    onClick={() => movePage(totalPages - 1)}
                    disabled={!hasNext}
                    className="h-8 rounded-lg border border-slate-300 bg-white px-2.5 text-sm text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  마지막
                </button>
              </div>
            </div>
          </SectionCard>
        </div>

        {isCreateModalOpen && (
            <CreateKnowledgeModal
                open={isCreateModalOpen}
                saving={saving}
                onClose={() => setIsCreateModalOpen(false)}
                onSubmit={handleCreate}
            />
        )}
      </>
  );
}
