import React, { useEffect, useMemo, useRef, useState } from 'react';
import LabeledInput from '../components/common/LabeledInput';
import SectionCard from '../components/common/SectionCard';
import CreatePatchHistoryModal from '../components/modal/CreatePatchHistoryModal';
import ExcelUploadModal from '../components/modal/ExcelUploadModal';
import { API_BASE, emptyForm, infraOptions } from '../constants/patchHistoryOptions';
import PageTitle from '../components/common/PageTitle';
import SaveScreenButton from '../components/common/SaveScreenButton';

// 페이지 버튼 목록을 만듭니다.
// 전체 페이지가 많을 때는 첫 페이지, 마지막 페이지, 현재 페이지 주변만 보여주고 중간은 ... 처리합니다.
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

// 등록일 표시 포맷
// 예: 2026-05-14T10:30:20 → 2026-05-14 10:30
function formatDateTime(value) {
  if (!value) {
    return '-';
  }

  return String(value).replace('T', ' ').slice(0, 16);
}

function formatDate(value) {
  if (!value) {
    return '-';
  }

  return String(value).slice(0, 10);
}

function formatAuthorName(value) {
  if (!value) {
    return '-';
  }

  return String(value).trim().toLowerCase() === 'excel-upload' ? 'excel' : value;
}

// 검색 영역에서 반복되는 input 스타일입니다.
const searchInputClass =
  'h-9 w-full rounded-lg border border-slate-300 px-3 text-sm outline-none ring-0 focus:border-slate-500';

// 검색 영역에서 반복되는 버튼 스타일입니다.
const toolbarButtonClass =
  'h-9 shrink-0 rounded-lg px-3 text-sm font-semibold shadow-sm transition';

const AUTO_SEARCH_DELAY_MS = 350;

const patchHistoryRelationOptions = [
  { value: 'ALL', label: '전체' },
  { value: 'BEFORE', label: '이전' },
  { value: 'AFTER', label: '이후' },
];

// 패치이력 메인 페이지
// 검색, 엑셀 업로드 버튼, 추가 버튼, 목록, 새창 상세보기를 담당합니다.
function getFilterLabel(selectedValues, emptyLabel) {
  if (selectedValues.length === 0) {
    return emptyLabel;
  }

  if (selectedValues.length === 1) {
    return selectedValues[0];
  }

  return `${selectedValues[0]} 외 ${selectedValues.length - 1}`;
}

function toggleSelectedValue(values, value) {
  return values.includes(value)
    ? values.filter((item) => item !== value)
    : [...values, value];
}

function MultiSelectDropdown({
  label,
  options,
  selectedValues,
  onChange,
  emptyLabel = '전체',
  disabled = false,
}) {
  const dropdownRef = useRef(null);
  const [open, setOpen] = useState(false);
  const [optionKeyword, setOptionKeyword] = useState('');
  const selectedSet = useMemo(() => new Set(selectedValues), [selectedValues]);
  const filteredOptions = useMemo(() => {
    const keyword = optionKeyword.trim().toLowerCase();

    if (!keyword) {
      return options;
    }

    return options.filter((option) => String(option).toLowerCase().includes(keyword));
  }, [optionKeyword, options]);

  const closeDropdown = () => {
    setOpen(false);
    setOptionKeyword('');
  };

  const toggleOpen = () => {
    setOpen((prev) => {
      const nextOpen = !prev;

      if (!nextOpen) {
        setOptionKeyword('');
      }

      return nextOpen;
    });
  };

  useEffect(() => {
    if (!open) {
      return undefined;
    }

    const handleDocumentMouseDown = (e) => {
      if (!dropdownRef.current?.contains(e.target)) {
        closeDropdown();
      }
    };

    const handleDocumentKeyDown = (e) => {
      if (e.key === 'Escape') {
        closeDropdown();
      }
    };

    document.addEventListener('mousedown', handleDocumentMouseDown);
    document.addEventListener('keydown', handleDocumentKeyDown);

    return () => {
      document.removeEventListener('mousedown', handleDocumentMouseDown);
      document.removeEventListener('keydown', handleDocumentKeyDown);
    };
  }, [open]);

  return (
    <div ref={dropdownRef} className="relative">
      <LabeledInput label={label} compact>
        <button
          type="button"
          disabled={disabled}
          onClick={toggleOpen}
          className={`${searchInputClass} flex items-center justify-between bg-white text-left disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400`}
        >
          <span className="truncate">{getFilterLabel(selectedValues, emptyLabel)}</span>
          <span className="ml-2 text-xs text-slate-500">▼</span>
        </button>
      </LabeledInput>

      {open && (
        <div className="absolute left-0 z-30 mt-1 max-h-72 w-64 overflow-y-auto rounded-lg border border-slate-200 bg-white py-2 shadow-lg">
          <div className="px-2 pb-2">
            <input
              value={optionKeyword}
              onChange={(e) => setOptionKeyword(e.target.value)}
              className="h-8 w-full rounded-md border border-slate-300 px-2 text-sm outline-none focus:border-slate-500"
              placeholder={`${label} 검색`}
              autoFocus
            />
          </div>

          <label className="flex cursor-pointer items-center gap-2 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-50">
            <input
              type="checkbox"
              checked={selectedValues.length === 0}
              onChange={() => onChange([])}
            />
            <span className="flex-1">(모두)</span>
          </label>

          {filteredOptions.map((option) => (
            <label
              key={option}
              className="flex cursor-pointer items-center gap-2 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-50"
            >
              <input
                type="checkbox"
                checked={selectedSet.has(option)}
                onChange={() => onChange(toggleSelectedValue(selectedValues, option))}
              />
              <span className="min-w-0 flex-1 truncate" title={option}>{option}</span>
            </label>
          ))}

          {filteredOptions.length === 0 && (
            <div className="px-3 py-3 text-center text-sm text-slate-400">
              검색 결과가 없습니다.
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function getInitialSearchParam(name, fallback = '') {
  const params = new URLSearchParams(window.location.search);
  return params.get(name) || fallback;
}

export default function PatchHistoryPage() {
  const initialParams = new URLSearchParams(window.location.search);
  // 목록 데이터 상태
  const [patchHistories, setPatchHistories] = useState([]);

  // 등록 모달 상태
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);

  // 엑셀 업로드 모달 상태
  const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);
  const [file, setFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [isUploadFilesModalOpen, setIsUploadFilesModalOpen] = useState(false);
  const [uploadFiles, setUploadFiles] = useState([]);
  const [loadingUploadFiles, setLoadingUploadFiles] = useState(false);
  const [excludedItems, setExcludedItems] = useState([]);
  const [isExcludedModalOpen, setIsExcludedModalOpen] = useState(false);

  // 로딩/메시지 상태
  const [loadingList, setLoadingList] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  // 검색 조건 상태
  const [searchKeyword, setSearchKeyword] = useState(() => getInitialSearchParam('keyword'));
  const customerFilter = '';
  const [selectedInfraTypes, setSelectedInfraTypes] = useState([]);
  const [infraFilter, setInfraFilter] = useState(() => getInitialSearchParam('infraType', 'ALL'));
  const statusFilter = 'ALL';
  const [selectedCategories, setSelectedCategories] = useState([]);
  const [selectedDeploymentVersions, setSelectedDeploymentVersions] = useState(
    () => initialParams.getAll('deploymentVersions')
  );
  const [patchHistoryRelation, setPatchHistoryRelation] = useState(() => {
    const initialRelation = initialParams.get('patchHistoryRelation');
    const hasSingleReferenceVersion = initialParams.getAll('deploymentVersions').length === 1;
    return hasSingleReferenceVersion && ['BEFORE', 'AFTER'].includes(initialRelation)
      ? initialRelation
      : 'ALL';
  });
  const [categoryOptions, setCategoryOptions] = useState([]);
  const [categoryFilter, setCategoryFilter] = useState(initialParams.get('category') || '');
  const [deploymentVersionFilter, setDeploymentVersionFilter] = useState(initialParams.get('deploymentVersion') || '');
  const [isDetailSearchOpen, setIsDetailSearchOpen] = useState(initialParams.get('detailSearch') === 'true');
  const [detailInfraFilter, setDetailInfraFilter] = useState(initialParams.get('detailInfraType') || '');
  const [detailType, setDetailType] = useState(initialParams.get('detailType') || 'WEB');
  const [detailDeploymentVersion, setDetailDeploymentVersion] = useState(initialParams.get('detailDeploymentVersion') || '');
  const [detailVersionRelation, setDetailVersionRelation] = useState(initialParams.get('detailVersionRelation') || 'ALL');
  const [deploymentVersionOptions, setDeploymentVersionOptions] = useState([]);
  const [loadingFilterOptions, setLoadingFilterOptions] = useState(false);
  // 기간 검색 조건. 기본값은 비워두어 기존처럼 전체 기간을 조회한다.
  const [startDateFilter] = useState('');
  const [endDateFilter] = useState('');

  // 페이징 상태
  // 기본 표시 개수는 50개이며, 사용자가 select로 변경할 수 있게 합니다.
  const [page, setPage] = useState(Number(initialParams.get('page') || 0));
  const [size, setSize] = useState(Number(initialParams.get('size') || 50));
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [hasPrevious, setHasPrevious] = useState(false);

  // 현재 페이지 기준으로 하단 페이지 버튼 목록을 계산합니다.
  const pageItems = useMemo(() => buildPageItems(page, totalPages), [page, totalPages]);
  const autoSearchInitializedRef = useRef(false);

  const fetchFilterOptions = async (targetCategories = selectedCategories) => {
    setLoadingFilterOptions(true);

    try {
      const params = new URLSearchParams();
      targetCategories.forEach((category) => params.append('categories', category));

      const res = await fetch(`${API_BASE}/api/patch-histories/filter-options?${params.toString()}`);
      const result = await res.json();

      if (!res.ok || !result.success) {
        throw new Error(result.message || '배포버전 목록을 조회하지 못했습니다.');
      }

      const nextDeploymentVersions = result.data?.deploymentVersions || [];

      setCategoryOptions(result.data?.categories || []);
      setDeploymentVersionOptions(nextDeploymentVersions);
      setSelectedDeploymentVersions((prev) => {
        const next = prev.filter((version) => nextDeploymentVersions.includes(version));
        return next.length === prev.length ? prev : next;
      });
    } catch (e) {
      console.error(e);
      setCategoryOptions([]);
      setDeploymentVersionOptions([]);
    } finally {
      setLoadingFilterOptions(false);
    }
  };

  // 검색 조건과 페이지 정보를 기준으로 목록을 조회합니다.
  const fetchPatchHistories = async (targetPage = page, targetSize = size) => {
    setLoadingList(true);
    setError('');

    try {
      const params = new URLSearchParams();

      if (searchKeyword.trim()) params.append('keyword', searchKeyword.trim());
      if (customerFilter.trim()) params.append('customerName', customerFilter.trim());
      selectedInfraTypes.forEach((infraType) => params.append('infraTypes', infraType));
      selectedCategories.forEach((category) => params.append('categories', category));
      if (patchHistoryRelation === 'ALL') {
        selectedDeploymentVersions.forEach((version) => params.append('deploymentVersions', version));
      } else if (selectedDeploymentVersions.length === 1) {
        params.append('referenceDeploymentVersion', selectedDeploymentVersions[0]);
        params.append('versionRelation', patchHistoryRelation);
      }
      if (statusFilter !== 'ALL') params.append('status', statusFilter);
      // 기간 조건이 있을 때만 전송한다.
      // 값이 없으면 백엔드에서 전체 기간으로 조회한다.
      if (startDateFilter) params.append('startDate', startDateFilter);
      if (endDateFilter) params.append('endDate', endDateFilter);

      // 백엔드 search API의 page, size 파라미터로 페이징을 제어합니다.
      params.append('page', targetPage);
      params.append('size', targetSize);

      const res = await fetch(`${API_BASE}/api/patch-histories/search?${params.toString()}`);
      const result = await res.json();

      if (!res.ok || !result.success) {
        throw new Error(result.message || '목록 조회에 실패했습니다.');
      }

      const pageData = result.data;
      setPatchHistories(pageData.content || []);
      setPage(pageData.page);
      setTotalPages(pageData.totalPages);
      setTotalElements(pageData.totalElements);
      setHasNext(pageData.hasNext);
      setHasPrevious(pageData.hasPrevious);
    } catch (e) {
      setError(e.message || '목록 조회 중 오류가 발생했습니다.');
    } finally {
      setLoadingList(false);
    }
  };

  // 한 페이지에 표시할 목록 개수를 바꾸고 첫 페이지부터 다시 조회합니다.
  const handleSizeChange = (e) => {
    const nextSize = Number(e.target.value);
    setSize(nextSize);
    fetchPatchHistories(0, nextSize);
  };

  // 페이지 번호 버튼 클릭 시 해당 페이지를 조회합니다.
  const movePage = (targetPage) => {
    if (targetPage < 0 || targetPage >= totalPages || targetPage === page) {
      return;
    }

    fetchPatchHistories(targetPage, size);
  };

  // 패치이력 상세보기를 별도 브라우저 창으로 엽니다.
  const openDetailWindow = (id) => {
    const url = `${window.location.origin}${window.location.pathname}?popup=patch-history-detail&id=${id}`;
    const features = 'width=1200,height=820,left=120,top=80,scrollbars=yes,resizable=yes';
    window.open(url, `patch-history-detail-${id}`, features);
  };

  const buildSavedScreenUrl = () => {
    const params = new URLSearchParams();
    params.set('menu', 'PATCH_HISTORY');
    if (searchKeyword.trim()) params.set('keyword', searchKeyword.trim());
    if (infraFilter !== 'ALL') params.set('infraType', infraFilter);
    if (categoryFilter.trim()) params.set('category', categoryFilter.trim());
    if (deploymentVersionFilter.trim()) params.set('deploymentVersion', deploymentVersionFilter.trim());
    selectedDeploymentVersions.forEach((version) => params.append('deploymentVersions', version));
    if (patchHistoryRelation !== 'ALL' && selectedDeploymentVersions.length === 1) {
      params.set('patchHistoryRelation', patchHistoryRelation);
    }
    if (isDetailSearchOpen) {
      params.set('detailSearch', 'true');
      if (detailInfraFilter.trim()) params.set('detailInfraType', detailInfraFilter.trim());
      if (detailType) params.set('detailType', detailType);
      if (detailDeploymentVersion.trim()) params.set('detailDeploymentVersion', detailDeploymentVersion.trim());
      if (detailVersionRelation !== 'ALL') params.set('detailVersionRelation', detailVersionRelation);
    }
    params.set('page', String(page));
    params.set('size', String(size));

    return `${window.location.origin}${window.location.pathname}?${params.toString()}`;
  };

  // 파일 선택 시 선택된 파일을 상태에 저장합니다.
  const handleFileChange = (e) => {
    setFile(e.target.files[0]);
  };

  const fetchUploadFiles = async () => {
    setLoadingUploadFiles(true);

    try {
      const res = await fetch(`${API_BASE}/api/patch-histories/upload-files`);
      const result = await res.json();

      if (!res.ok || !result.success) {
        throw new Error(result.message || '등록 파일 리스트를 조회하지 못했습니다.');
      }

      setUploadFiles(result.data || []);
      setIsUploadFilesModalOpen(true);
    } catch (e) {
      console.error(e);
      alert(e.message || '등록 파일 리스트 조회 중 오류가 발생했습니다.');
    } finally {
      setLoadingUploadFiles(false);
    }
  };

  // 엑셀 업로드 처리
  const handleUpload = async () => {
    if (uploading) {
      return;
    }

    if (!file) {
      alert('업로드할 엑셀 파일을 선택하세요.');
      return;
    }

    const formData = new FormData();
    formData.append('file', file);

    setUploading(true);

    try {
      const res = await fetch(`${API_BASE}/api/patch-histories/upload`, {
        method: 'POST',
        body: formData,
      });

      const text = await res.text();

      if (!res.ok) {
        throw new Error(text || '엑셀 업로드에 실패했습니다.');
      }

      const result = JSON.parse(text);

      const uploadResult = result.data;
      const savedCount = typeof uploadResult === 'number' ? uploadResult : uploadResult?.savedCount || 0;
      const nextExcludedItems = typeof uploadResult === 'number' ? [] : uploadResult?.excludedItems || [];
      result.data = savedCount;

      if (!result.success) {
        throw new Error(result.message || '엑셀 업로드에 실패했습니다.');
      }

      alert(`엑셀 업로드 완료: ${result.data}건 등록`);
      setFile(null);
      setIsUploadModalOpen(false);
      await fetchPatchHistories(0, size);

      if (nextExcludedItems.length > 0 && window.confirm('등록제외 항목이 있습니다 확인하겠습니까?')) {
        setExcludedItems(nextExcludedItems);
        setIsExcludedModalOpen(true);
      }
    } catch (e) {
      console.error(e);
      alert(e.message || '엑셀 업로드 중 오류가 발생했습니다.');
    } finally {
      setUploading(false);
    }
  };

  // 등록 폼 입력값 변경
  const handleChange = (key, value) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  // 신규 패치이력 등록 처리
  const handleSubmit = async (e) => {
    e.preventDefault();
    setSaving(true);
    setMessage('');
    setError('');

    try {
      const res = await fetch(`${API_BASE}/api/patch-histories/json`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(form),
      });

      const data = await res.json();

      if (!res.ok || !data.success) {
        throw new Error(data.message || '등록에 실패했습니다.');
      }

      setMessage(`등록 완료 (ID: ${data.data})`);
      setForm(emptyForm);
      setIsCreateModalOpen(false);
      await fetchPatchHistories(0, size);
    } catch (e) {
      setError(e.message || '등록 중 오류가 발생했습니다.');
    } finally {
      setSaving(false);
    }
  };

  // 최초 진입 시 목록을 1회 조회합니다.
  useEffect(() => {
    fetchFilterOptions([]);
    fetchPatchHistories(page, size);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    fetchFilterOptions(selectedCategories);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedCategories]);

  useEffect(() => {
    if (!autoSearchInitializedRef.current) {
      autoSearchInitializedRef.current = true;
      return undefined;
    }

    const timer = setTimeout(() => {
      fetchPatchHistories(0, size);
    }, AUTO_SEARCH_DELAY_MS);

    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    searchKeyword,
    selectedInfraTypes,
    selectedCategories,
    selectedDeploymentVersions,
    patchHistoryRelation,
  ]);

  return (
    <>
      <PageTitle
        title="패치이력"
        description="패치리스트 엑셀을 업로드하고 패치 이력을 조회하는 화면입니다."
      />

      {(message || error) && (
        <div className={`mb-4 rounded-xl border px-4 py-3 text-sm ${
          error
            ? 'border-red-200 bg-red-50 text-red-700'
            : 'border-emerald-200 bg-emerald-50 text-emerald-700'
        }`}>
          {error || message}
        </div>
      )}

      <div className="space-y-5">
        <SectionCard className="p-3">
          <div className="flex flex-wrap items-end gap-2">
            <div className="w-full sm:w-[280px]">
              <LabeledInput label="검색어" compact>
                <input
                  className={searchInputClass}
                  value={searchKeyword}
                  onChange={(e) => setSearchKeyword(e.target.value)}
                  placeholder="제목, 내용"
                />
              </LabeledInput>
            </div>

            <div className="w-full sm:w-[180px]">
              <MultiSelectDropdown
                label="구분"
                options={categoryOptions}
                selectedValues={selectedCategories}
                onChange={setSelectedCategories}
                disabled={loadingFilterOptions}
              />
            </div>

            <div className="w-full sm:w-[220px]">
              <MultiSelectDropdown
                label="배포버전"
                options={deploymentVersionOptions}
                selectedValues={selectedDeploymentVersions}
                onChange={(nextValues) => {
                  setSelectedDeploymentVersions(nextValues);
                  if (nextValues.length !== 1) {
                    setPatchHistoryRelation('ALL');
                  }
                }}
                disabled={loadingFilterOptions}
              />
            </div>

            <div className="w-full sm:w-[140px]">
              <LabeledInput label="패치이력" compact>
                <select
                  className={searchInputClass}
                  value={patchHistoryRelation}
                  onChange={(e) => setPatchHistoryRelation(e.target.value)}
                  disabled={loadingFilterOptions}
                >
                  {patchHistoryRelationOptions.map((option) => (
                    <option
                      key={option.value}
                      value={option.value}
                      disabled={option.value !== 'ALL' && selectedDeploymentVersions.length !== 1}
                    >
                      {option.label}
                    </option>
                  ))}
                </select>
              </LabeledInput>
            </div>

            <div className="ml-auto flex w-full flex-wrap gap-2 sm:w-auto sm:justify-end">
              <SaveScreenButton
                title={`패치이력${searchKeyword.trim() ? ` - ${searchKeyword.trim()}` : ''}`}
                url={buildSavedScreenUrl()}
                className="w-full sm:w-[132px]"
              />

              <button
                type="button"
                onClick={() => setIsUploadModalOpen(true)}
                className={`${toolbarButtonClass} inline-flex w-full items-center justify-center gap-2 whitespace-nowrap border border-slate-300 bg-white text-slate-700 hover:bg-slate-50 sm:w-[132px]`}
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
                엑셀 업로드
              </button>

              <button
                type="button"
                onClick={() => setIsCreateModalOpen(true)}
                className={`${toolbarButtonClass} w-full bg-slate-900 text-white hover:bg-slate-800 sm:w-[88px]`}
              >
                추가
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

        <SectionCard          title={(
            <span className="flex flex-wrap items-center gap-3">
              <span>패치이력 목록</span>
              <button
                type="button"
                onClick={fetchUploadFiles}
                disabled={loadingUploadFiles}
                className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {loadingUploadFiles ? '조회 중...' : '등록 파일 리스트'}
              </button>
            </span>
          )}
          description="목록 행을 클릭하면 상세보기가 새 창으로 열립니다."
        >
          <div className="overflow-hidden rounded-2xl border border-slate-200">
            <div className="overflow-x-auto">
              <table className="w-full min-w-[1400px] table-fixed divide-y divide-slate-200 text-sm">
                <thead className="bg-slate-100">
                  <tr>
                    <th className="w-[7%] px-4 py-3 text-left font-semibold">INFRA</th>
                    <th className="w-[11%] px-4 py-3 text-left font-semibold">구분</th>
                    <th className="w-[11%] px-4 py-3 text-left font-semibold">배포버전</th>
                    <th className="w-[10%] px-4 py-3 text-left font-semibold">완료일</th>
                    <th className="w-[19%] px-4 py-3 text-left font-semibold">제목</th>
                    <th className="w-[25%] px-4 py-3 text-left font-semibold">내용</th>
                    <th className="w-[10%] px-4 py-3 text-left font-semibold">등록일</th>
                    <th className="w-[7%] px-4 py-3 text-center font-semibold">작성자</th>
                  </tr>
                </thead>

                <tbody className="divide-y divide-slate-100 bg-white">
                  {loadingList ? (
                    <tr>
                      <td colSpan={8} className="px-4 py-8 text-center text-slate-500">
                        불러오는 중...
                      </td>
                    </tr>
                  ) : patchHistories.length === 0 ? (
                    <tr>
                      <td colSpan={8} className="px-4 py-8 text-center text-slate-500">
                        등록된 패치이력이 없습니다.
                      </td>
                    </tr>
                  ) : (
                    patchHistories.map((patchHistory) => (
                      <tr
                        key={patchHistory.id}
                        onClick={() => openDetailWindow(patchHistory.id)}
                        className="cursor-pointer transition hover:bg-slate-50"
                      >
                        <td className="px-4 py-3">
                          <div className="truncate" title={patchHistory.infraType || '-'}>
                            {patchHistory.infraType || '-'}
                          </div>
                        </td>

                        <td className="px-4 py-3 text-slate-700">
                          <div className="max-w-[180px] truncate">
                            {patchHistory.category || '-'}
                          </div>
                        </td>

                        <td className="whitespace-nowrap px-4 py-3 text-slate-700">
                          {patchHistory.deploymentVersion || '-'}
                        </td>

                        <td className="whitespace-nowrap px-4 py-3 text-slate-700">
                          {formatDate(patchHistory.completedDate)}
                        </td>

                        <td className="px-4 py-3">
                          <div className="max-w-[320px] truncate font-medium text-slate-900">
                            {patchHistory.title || '-'}
                          </div>
                        </td>

                        <td className="px-4 py-3 text-slate-700">
                          <div className="max-w-[460px] truncate">
                            {patchHistory.content || patchHistory.symptomDetail || '-'}
                          </div>
                        </td>

                        <td className="whitespace-nowrap px-4 py-3 text-slate-700">
                          {formatDateTime(patchHistory.createdAt)}
                        </td>

                        <td className="px-4 py-3 text-center">
                          <div className="truncate" title={formatAuthorName(patchHistory.authorName)}>
                            {formatAuthorName(patchHistory.authorName)}
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>

          <div className="mt-4 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex flex-wrap items-center gap-3 text-sm text-slate-500">
              <span>
                전체 {totalElements}건 / {totalPages === 0 ? 0 : page + 1}페이지 / {totalPages}페이지
              </span>

              <label className="flex items-center gap-2">
                <span>표시 개수</span>
                <select
                  value={size}
                  onChange={handleSizeChange}
                  className="h-8 rounded-lg border border-slate-300 bg-white px-2 text-sm text-slate-700 outline-none focus:border-slate-500"
                >
                  <option value={5}>5개</option>
                  <option value={10}>10개</option>
                  <option value={20}>20개</option>
                  <option value={50}>50개</option>
                </select>
              </label>
            </div>

            <div className="flex flex-wrap items-center gap-1 lg:justify-end">
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

              {pageItems.map((item) => (
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
              ))}

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
        <CreatePatchHistoryModal
          form={form}
          saving={saving}
          handleChange={handleChange}
          handleSubmit={handleSubmit}
          onReset={() => setForm(emptyForm)}
          onClose={() => setIsCreateModalOpen(false)}
        />
      )}

      {isUploadModalOpen && (
        <ExcelUploadModal
          file={file}
          onFileChange={handleFileChange}
          onUpload={handleUpload}
          isUploading={uploading}
          onClose={() => setIsUploadModalOpen(false)}
        />
      )}

      {isUploadFilesModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 px-4">
          <div className="w-full max-w-[720px] rounded-2xl bg-white shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
              <h2 className="text-xl font-semibold text-slate-900">등록 파일 리스트</h2>
              <button
                type="button"
                onClick={() => setIsUploadFilesModalOpen(false)}
                className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 hover:bg-slate-50"
              >
                닫기
              </button>
            </div>

            <div className="max-h-[520px] overflow-y-auto px-6 py-5">
              {uploadFiles.length === 0 ? (
                <div className="rounded-lg border border-slate-200 bg-slate-50 px-4 py-6 text-center text-sm text-slate-500">
                  등록된 파일이 없습니다.
                </div>
              ) : (
                <div className="space-y-2">
                  {uploadFiles.map((item) => (
                    <div key={item.id} className="rounded-lg border border-slate-200 px-4 py-3">
                      <div className="font-medium text-slate-900">{item.fileName}</div>
                      <div className="mt-1 text-xs text-slate-500">
                        {formatDateTime(item.createdAt)} · 등록 {item.savedCount}건 · 제외 {item.excludedCount}건
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {isExcludedModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 px-4">
          <div className="w-full max-w-[820px] rounded-2xl bg-white shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
              <h2 className="text-xl font-semibold text-slate-900">등록 제외 항목</h2>
              <button
                type="button"
                onClick={() => setIsExcludedModalOpen(false)}
                className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 hover:bg-slate-50"
              >
                닫기
              </button>
            </div>

            <div className="max-h-[520px] overflow-y-auto px-6 py-5">
              <pre className="whitespace-pre-wrap rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm leading-6 text-slate-800">
                {excludedItems
                  .map((item, index) => (
                    `${index + 1}. [${item.sheetName || '-'} / ${item.rowNumber || '-'}행] ${item.reason || '등록 제외'}\n${item.title || '-'}`
                  ))
                  .join('\n\n')}
              </pre>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
