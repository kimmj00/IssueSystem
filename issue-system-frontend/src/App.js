import React, { useEffect, useState } from 'react';

const API_BASE =
    process.env.NODE_ENV === 'development'
        ? 'http://localhost:8080'
        : '';

// 인프라 옵션
// 현재 백엔드 enum과 동일한 값을 사용해야 검색/등록이 정상 동작함
const infraOptions = [
  'EMS', '예방점검', 'ERMS', 'SMS', 'NMS', 'DBMS', 'FMS', 'IMS',
  'SYSLOG', 'TRAP', 'TMS', 'APM', 'BMS', 'STMS', 'RTMS', 'VMS',
  'OAM', 'WNMS', 'CMS', 'K8S', 'TRMS', 'NPM', 'BRMS'
];

const categoryOptions = ['Tomcat', 'JAVA', 'WEB', 'DB', 'Agent', 'Manager', '보안취약'];

// 상태 옵션
const statusOptions = ['OPEN', 'RESOLVED', 'CLOSED'];

// 등록 폼 초기값
const emptyForm = {
  title: '',
  infraType: 'EMS',
  systemName: '',
  customerName: '',
  category: '',
  versionInfo: '',          // DB버전
  deploymentVersion: '',    // 배포 버전
  status: 'RESOLVED',
  symptomSummary: '',
  symptomDetail: '',
  causeDetail: '',
  actionDetail: '',
  tags: '',
  authorName: '',
};

// 카드 공통 UI
function SectionCard({ title, description, children }) {
  return (
      <section className="rounded-2xl bg-white shadow-sm border border-slate-200 p-5">
        <div className="mb-4">
          <h2 className="text-xl font-semibold text-slate-900">{title}</h2>
          {description ? <p className="mt-1 text-sm text-slate-500">{description}</p> : null}
        </div>
        {children}
      </section>
  );
}

// 라벨 + 입력 컴포넌트
function LabeledInput({ label, children }) {
  return (
      <label className="block">
        <div className="mb-2 text-sm font-medium text-slate-700">{label}</div>
        {children}
      </label>
  );
}

// 상태/인프라 Badge
function Badge({ children }) {
  return (
      <span className="inline-flex items-center rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-700">
      {children}
    </span>
  );
}

// 상세 블록 UI
function DetailBlock({ title, value }) {
  return (
      <div>
        <div className="mb-2 text-sm font-semibold text-slate-700">{title}</div>
        <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 whitespace-pre-wrap text-sm text-slate-800 min-h-14">
          {value || '-'}
        </div>
      </div>
  );
}

export default function App() {
  // 목록/상세/폼 상태
  const [issues, setIssues] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [selectedIssue, setSelectedIssue] = useState(null);
  const [form, setForm] = useState(emptyForm);

  // 로딩/메시지 상태
  const [loadingList, setLoadingList] = useState(false);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  // 검색 조건 상태
  const [searchKeyword, setSearchKeyword] = useState('');
  const [customerFilter, setCustomerFilter] = useState('');
  const [infraFilter, setInfraFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [categoryFilter, setCategoryFilter] = useState('');
  const [deploymentVersionFilter, setDeploymentVersionFilter] = useState('');

  // 페이징 상태
  const [page, setPage] = useState(0);
  const [size] = useState(5);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [hasPrevious, setHasPrevious] = useState(false);

  // 파일 상태를 관리하는 useState 훅
  const [file, setFile] = useState(null);

  // 파일 선택 시, 선택된 파일을 상태에 저장하는 함수
  const handleFileChange = (e) => {
    // 선택한 파일을 상태에 저장
    setFile(e.target.files[0]);
  };

  // 업로드 버튼 클릭 시 실행되는 함수
  const handleUpload = async () => {
    if (!file) {
      alert('업로드할 엑셀 파일을 선택하세요.');
      return;
    }

    const formData = new FormData();
    formData.append('file', file);

    try {
      const res = await fetch(`${API_BASE}/api/issue-cases/upload`, {
        method: 'POST',
        body: formData,
      });

      const text = await res.text();


      if (!res.ok) {
        throw new Error(text || '엑셀 업로드에 실패했습니다.');
      }

      const result = JSON.parse(text);

      if (!result.success) {
        throw new Error(result.message || '엑셀 업로드에 실패했습니다.');
      }

      alert(`엑셀 업로드 완료: ${result.data}건 등록`);
      setFile(null);
      await fetchIssues(0);
    } catch (error) {
      console.error(error);
      alert(error.message || '엑셀 업로드 중 오류가 발생했습니다.');
    }
  };

  /**
   * 목록 조회
   * - 검색 조건 + 페이지 정보를 query string으로 전달
   * - 기본값은 현재 page지만, 검색 시에는 0페이지로 호출
   */
  const fetchIssues = async (targetPage = page) => {
    setLoadingList(true);
    setError('');

    try {
      const params = new URLSearchParams();

      if (searchKeyword.trim()) {
        params.append('keyword', searchKeyword.trim());
      }

      if (customerFilter.trim()) {
        params.append('customerName', customerFilter.trim());
      }

      if (categoryFilter.trim()) {
        params.append('category', categoryFilter.trim());
      }

      if (deploymentVersionFilter.trim()) {
        params.append('deploymentVersion', deploymentVersionFilter.trim());
      }

      if (infraFilter !== 'ALL') {
        params.append('infraType', infraFilter);
      }

      if (statusFilter !== 'ALL') {
        params.append('status', statusFilter);
      }

      params.append('page', targetPage);
      params.append('size', size);

      const url = `${API_BASE}/api/issue-cases/search?${params.toString()}`;
      const res = await fetch(url);
      const result = await res.json();

      if (!res.ok || !result.success) {
        throw new Error(result.message || '목록 조회에 실패했습니다.');
      }

      const pageData = result.data;

      setIssues(pageData.content || []);
      setPage(pageData.page);
      setTotalPages(pageData.totalPages);
      setTotalElements(pageData.totalElements);
      setHasNext(pageData.hasNext);
      setHasPrevious(pageData.hasPrevious);

      // 현재 선택된 이슈가 새 목록에 없으면 상세 초기화
      if (selectedId && !(pageData.content || []).some(issue => issue.id === selectedId)) {
        setSelectedId(null);
        setSelectedIssue(null);
      }
    } catch (e) {
      setError(e.message || '목록 조회 중 오류가 발생했습니다.');
    } finally {
      setLoadingList(false);
    }
  };

  /**
   * 상세 조회
   */
  const fetchDetail = async (id) => {
    if (!id) return;

    setLoadingDetail(true);
    setError('');

    try {
      const res = await fetch(`${API_BASE}/api/issue-cases/${id}`);
      const data = await res.json();

      if (!res.ok || !data.success) {
        throw new Error(data.message || '상세 조회에 실패했습니다.');
      }

      setSelectedIssue(data.data);
    } catch (e) {
      setError(e.message || '상세 조회 중 오류가 발생했습니다.');
      setSelectedIssue(null);
    } finally {
      setLoadingDetail(false);
    }
  };

  /**
   * 최초 진입 시 1회 목록 조회
   */
  useEffect(() => {
    fetchIssues(0);
  }, []);

  /**
   * 선택된 이슈가 바뀌면 상세 조회
   */
  useEffect(() => {
    if (selectedId != null) {
      fetchDetail(selectedId);
    }
  }, [selectedId]);

  /**
   * 폼 입력값 변경
   */
  const handleChange = (key, value) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  /**
   * 등록 처리
   */
  const handleSubmit = async (e) => {
    e.preventDefault();
    setSaving(true);
    setMessage('');
    setError('');

    try {
      const res = await fetch(`${API_BASE}/api/issue-cases/json`, {
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

      // 등록 후 첫 페이지부터 다시 조회
      await fetchIssues(0);
      setSelectedId(data.data);
    } catch (e2) {
      setError(e2.message || '등록 중 오류가 발생했습니다.');
    } finally {
      setSaving(false);
    }
  };

  return (
      <div className="min-h-screen w-full overflow-x-hidden bg-slate-50 text-slate-900">
        <div className="mx-auto w-full max-w-[1760px] px-4 py-6 sm:px-6 lg:px-8">
          {/* 페이지 제목 */}
          <div className="mb-8">
            <h1 className="text-3xl font-bold tracking-tight">이슈관리 시스템</h1>
            <p className="mt-2 text-sm text-slate-500">
              고객사별 이슈를 기록하고, 동일 증상 사례를 빠르게 찾는 화면입니다.
            </p>
          </div>

          {/* 성공/에러 메시지 */}
          {(message || error) && (
              <div className={`mb-6 rounded-xl border px-4 py-3 text-sm ${error ? 'border-red-200 bg-red-50 text-red-700' : 'border-emerald-200 bg-emerald-50 text-emerald-700'}`}>
                {error || message}
              </div>
          )}

          {/*검색*/}
          <div className="grid grid-cols-1 gap-6 2xl:grid-cols-[minmax(0,1.45fr)_minmax(380px,0.75fr)]">
            <div className="space-y-6">
              {/* 목록 영역 */}
              <SectionCard title="이슈 목록" description="제목, 증상 요약, 고객사 기준으로 간단 검색이 가능합니다.">
                <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-[1fr_0.8fr_0.9fr_0.8fr_0.65fr_0.65fr_auto]">
                  <LabeledInput label="키워드">
                    <input
                        className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none ring-0 focus:border-slate-500"
                        value={searchKeyword}
                        onChange={(e) => setSearchKeyword(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') {
                            e.preventDefault();
                            fetchIssues(0);
                          }
                        }}
                        placeholder="제목, 증상, 태그"
                    />
                  </LabeledInput>

                  <LabeledInput label="고객사">
                    <input
                        className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none ring-0 focus:border-slate-500"
                        value={customerFilter}
                        onChange={(e) => setCustomerFilter(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') {
                            e.preventDefault();
                            fetchIssues(0);
                          }
                        }}
                        placeholder="고객사명"
                    />
                  </LabeledInput>

                  <LabeledInput label="구분">
                    <input
                        className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
                        value={categoryFilter}
                        onChange={(e) => setCategoryFilter(e.target.value)}
                        placeholder="예: Tomcat / DB / Agent"
                    />
                  </LabeledInput>

                  <LabeledInput label="배포버전">
                    <input
                        className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
                        value={deploymentVersionFilter}
                        onChange={(e) => setDeploymentVersionFilter(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') {
                            e.preventDefault();
                            fetchIssues(0);
                          }
                        }}
                        placeholder="예: 20251114.X"
                    />
                  </LabeledInput>

                  <LabeledInput label="인프라">
                    <select
                        className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
                        value={infraFilter}
                        onChange={(e) => setInfraFilter(e.target.value)}
                    >
                      <option value="ALL">전체</option>
                      {infraOptions.map((option) => (
                          <option key={option} value={option}>{option}</option>
                      ))}
                    </select>
                  </LabeledInput>

                  <LabeledInput label="상태">
                    <select
                        className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
                        value={statusFilter}
                        onChange={(e) => setStatusFilter(e.target.value)}
                    >
                      <option value="ALL">전체</option>
                      {statusOptions.map((option) => (
                          <option key={option} value={option}>{option}</option>
                      ))}
                    </select>
                  </LabeledInput>

                  <div className="flex items-end">
                    <button
                        type="button"
                        onClick={() => fetchIssues(0)}
                        className="h-[42px] min-w-[72px] rounded-lg bg-slate-900 px-3 text-sm font-semibold text-white shadow-sm hover:bg-slate-800"
                    >
                      검색
                    </button>
                  </div>
                </div>

                <div className="mt-5 overflow-hidden rounded-2xl border border-slate-200">
                  <div className="overflow-x-auto">
                    <table className="w-full min-w-[900px] divide-y divide-slate-200 text-sm">
                      <thead className="bg-slate-100">
                      <tr>
                        <th className="px-4 py-3 text-left font-semibold">ID</th>
                        <th className="px-4 py-3 text-left font-semibold">제목</th>
                        <th className="px-4 py-3 text-left font-semibold">인프라</th>
                        <th className="px-4 py-3 text-left font-semibold">고객사</th>
                        <th className="px-4 py-3 text-left font-semibold">상태</th>
                        <th className="px-4 py-3 text-left font-semibold">작성자</th>
                      </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100 bg-white">
                      {loadingList ? (
                          <tr>
                            <td colSpan={6} className="px-4 py-8 text-center text-slate-500">불러오는 중...</td>
                          </tr>
                      ) : issues.length === 0 ? (
                          <tr>
                            <td colSpan={6} className="px-4 py-8 text-center text-slate-500">등록된 이슈가 없습니다.</td>
                          </tr>
                      ) : (
                          issues.map((issue) => (
                              <tr
                                  key={issue.id}
                                  onClick={() => setSelectedId(issue.id)}
                                  className={`cursor-pointer transition hover:bg-slate-50 ${selectedId === issue.id ? 'bg-slate-50' : ''}`}
                              >
                                <td className="px-4 py-3">{issue.id}</td>
                                <td className="px-4 py-3">
                                  <div className="font-medium text-slate-900">{issue.title}</div>
                                  <div className="mt-1 text-xs text-slate-500">{issue.symptomSummary}</div>
                                </td>
                                <td className="px-4 py-3">{issue.infraType}</td>
                                <td className="px-4 py-3 whitespace-nowrap">
                                  {issue.customerName || '-'}
                                </td>
                                <td className="px-4 py-3"><Badge>{issue.status}</Badge></td>
                                <td className="px-4 py-3">{issue.authorName}</td>
                              </tr>
                          ))
                      )}
                      </tbody>
                    </table>
                  </div>
                </div>

                {/* 페이징 영역 */}
                <div className="mt-4 flex items-center justify-between">
                  <div className="text-sm text-slate-500">
                    전체 {totalElements}건 / {page + 1}페이지 / {totalPages}페이지
                  </div>

                  <div className="flex gap-2">
                    <button
                        type="button"
                        onClick={() => fetchIssues(page - 1)}
                        disabled={!hasPrevious}
                        className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      이전
                    </button>

                    <button
                        type="button"
                        onClick={() => fetchIssues(page + 1)}
                        disabled={!hasNext}
                        className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      다음
                    </button>
                  </div>
                </div>
              </SectionCard>

              {/* 상세 영역 */}
              <SectionCard title="이슈 상세" description="목록에서 선택한 사례의 원인과 조치 내용을 확인합니다.">
                {loadingDetail ? (
                    <div className="rounded-xl border border-dashed border-slate-300 px-4 py-8 text-center text-slate-500">
                      상세 정보를 불러오는 중입니다.
                    </div>
                ) : !selectedIssue ? (
                    <div className="rounded-xl border border-dashed border-slate-300 px-4 py-8 text-center text-slate-500">
                      목록에서 이슈를 선택하세요.
                    </div>
                ) : (
                    <div className="space-y-5">
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="text-2xl font-semibold">{selectedIssue.title}</h3>
                        <Badge>{selectedIssue.infraType}</Badge>
                        <Badge>{selectedIssue.status}</Badge>
                      </div>

                      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                        <div className="rounded-xl bg-slate-50 p-4">
                          <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">시스템명</div>
                          <div className="mt-1 text-sm text-slate-900">{selectedIssue.systemName}</div>
                        </div>

                        <div className="rounded-xl bg-slate-50 p-4">
                          <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">구분</div>
                          <div className="mt-1 text-sm text-slate-900">{selectedIssue.category || '-'}</div>
                        </div>

                        <div className="rounded-xl bg-slate-50 p-4">
                          <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">고객사</div>
                          <div className="mt-1 text-sm text-slate-900">{selectedIssue.customerName || '-'}</div>
                        </div>

                        <div className="rounded-xl bg-slate-50 p-4">
                          <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">DB버전</div>
                          <div className="mt-1 text-sm text-slate-900">{selectedIssue.versionInfo || '-'}</div>
                        </div>

                        <div className="rounded-xl bg-slate-50 p-4">
                          <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">배포 버전</div>
                          <div className="mt-1 text-sm text-slate-900">{selectedIssue.deploymentVersion || '-'}</div>
                        </div>

                        <div className="rounded-xl bg-slate-50 p-4">
                          <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">작성자</div>
                          <div className="mt-1 text-sm text-slate-900">{selectedIssue.authorName}</div>
                        </div>
                      </div>

                      <DetailBlock title="증상 요약" value={selectedIssue.symptomSummary} />
                      <DetailBlock title="증상 상세" value={selectedIssue.symptomDetail} />
                      <DetailBlock title="원인" value={selectedIssue.causeDetail} />
                      <DetailBlock title="조치 내용" value={selectedIssue.actionDetail} />
                      <DetailBlock title="태그" value={selectedIssue.tags} />
                    </div>
                )}
              </SectionCard>
            </div>

            {/* 등록 영역 */}
            <div className="space-y-6">
              <SectionCard title="엑셀 업로드" description="패치리스트 엑셀 파일을 업로드하면 자동으로 이슈 이력에 등록합니다.">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
                  <input
                      type="file"
                      accept=".xlsx,.xls"
                      onChange={handleFileChange}
                      className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm sm:w-[365px]"
                  />

                  <button
                      type="button"
                      onClick={handleUpload}
                      className="rounded-xl bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-slate-800"
                  >
                    엑셀 업로드
                  </button>
                </div>
              </SectionCard>

              <SectionCard title="이슈 등록" description="React에서 바로 백엔드 JSON 등록 API를 호출합니다.">
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                    <LabeledInput label="제목">
                      <input className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500" value={form.title} onChange={(e) => handleChange('title', e.target.value)} placeholder="예: DB 연결 끊김 사례" />
                    </LabeledInput>

                    <LabeledInput label="인프라 유형">
                      <select className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500" value={form.infraType} onChange={(e) => handleChange('infraType', e.target.value)}>
                        {infraOptions.map((option) => (
                            <option key={option} value={option}>{option}</option>
                        ))}
                      </select>
                    </LabeledInput>

                    <LabeledInput label="고객사명">
                      <input className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
                             value={form.customerName} onChange={(e) => handleChange('customerName', e.target.value)}
                             placeholder="예: A고객사" />
                    </LabeledInput>

                    <LabeledInput label="DB버전">
                      <input
                          className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
                          value={form.versionInfo}
                          onChange={(e) => handleChange('versionInfo', e.target.value)}
                          placeholder="예: PostgreSQL 14"
                      />
                    </LabeledInput>

                    <LabeledInput label="배포 버전">
                      <input
                          className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
                          value={form.deploymentVersion}
                          onChange={(e) => handleChange('deploymentVersion', e.target.value)}
                          placeholder="예: 20251114.X"
                      />
                    </LabeledInput>

                    <LabeledInput label="상태">
                      <select className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500" value={form.status} onChange={(e) => handleChange('status', e.target.value)}>
                        {statusOptions.map((option) => (
                            <option key={option} value={option}>{option}</option>
                        ))}
                      </select>
                    </LabeledInput>
                  </div>
                    <LabeledInput label="구분">
                      <div className="flex gap-2">
                        <select
                            className="w-[140px] shrink-0 rounded-xl border border-slate-300 px-2 py-2"
                            value={categoryOptions.includes(form.category) ? form.category : 'DIRECT'}
                            onChange={(e) => {
                              const value = e.target.value;

                              if (value === 'DIRECT') {
                                handleChange('category', '');
                              } else {
                                handleChange('category', value);
                              }
                            }}
                        >
                          <option value="">선택</option>
                          {categoryOptions.map((option) => (
                              <option key={option} value={option}>
                                {option}
                              </option>
                          ))}
                          <option value="DIRECT">직접입력</option>
                        </select>

                        <input
                            className={`flex-1 rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500 ${
                                categoryOptions.includes(form.category)
                                    ? 'bg-slate-100 text-slate-500 cursor-not-allowed'
                                    : 'bg-white'
                            }`}
                            value={form.category}
                            onChange={(e) => handleChange('category', e.target.value)}
                            disabled={categoryOptions.includes(form.category)}
                            placeholder="직접입력 또는 선택값"
                        />
                      </div>
                    </LabeledInput>

                  <LabeledInput label="증상 요약">
                    <input className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500" value={form.symptomSummary} onChange={(e) => handleChange('symptomSummary', e.target.value)} placeholder="예: DB 연결이 간헐적으로 끊김" />
                  </LabeledInput>

                  <LabeledInput label="증상 상세">
                    <textarea className="min-h-28 w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500" value={form.symptomDetail} onChange={(e) => handleChange('symptomDetail', e.target.value)} placeholder="상세 증상을 입력하세요" />
                  </LabeledInput>

                  <LabeledInput label="원인">
                    <textarea className="min-h-28 w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500" value={form.causeDetail} onChange={(e) => handleChange('causeDetail', e.target.value)} placeholder="원인을 입력하세요" />
                  </LabeledInput>

                  <LabeledInput label="조치 내용">
                    <textarea className="min-h-28 w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500" value={form.actionDetail} onChange={(e) => handleChange('actionDetail', e.target.value)} placeholder="조치 내용을 입력하세요" />
                  </LabeledInput>

                  <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                    <LabeledInput label="태그">
                      <input className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500" value={form.tags} onChange={(e) => handleChange('tags', e.target.value)} placeholder="예: DB, timeout, connection" />
                    </LabeledInput>

                    <LabeledInput label="작성자">
                      <input className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500" value={form.authorName} onChange={(e) => handleChange('authorName', e.target.value)} placeholder="예: 홍길동" />
                    </LabeledInput>
                  </div>

                  <div className="flex gap-3 pt-2">
                    <button type="submit" disabled={saving} className="rounded-xl bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400">
                      {saving ? '등록 중...' : '등록'}
                    </button>

                    <button
                        type="button"
                        onClick={() => setForm(emptyForm)}
                        className="rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
                    >
                      초기화
                    </button>
                  </div>
                </form>
              </SectionCard>
            </div>
          </div>
        </div>
      </div>
  );
}