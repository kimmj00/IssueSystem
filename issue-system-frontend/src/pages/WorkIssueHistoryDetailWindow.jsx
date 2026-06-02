import React, { useEffect, useMemo, useState } from 'react';
import DetailBlock from '../components/common/DetailBlock';
import { API_BASE } from '../constants/patchHistoryOptions';

function formatDateTime(value) {
  if (!value) return '-';

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleString('ko-KR');
}

function InfoBox({ title, value }) {
  return (
    <div className="rounded-lg bg-slate-50 px-3 py-3">
      <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">{title}</div>
      <div className="mt-1 break-words text-sm text-slate-900">{value || '-'}</div>
    </div>
  );
}

function endpointFor(type, id) {
  if (type === 'MAINTENANCE') {
    return `${API_BASE}/api/work-issue-histories/maintenance/${id}`;
  }

  return `${API_BASE}/api/work-issue-histories/projects/${id}`;
}

export default function WorkIssueHistoryDetailWindow() {
  const params = useMemo(() => new URLSearchParams(window.location.search), []);
  const id = params.get('id');
  const type = params.get('type') === 'MAINTENANCE' ? 'MAINTENANCE' : 'PROJECT';
  const typeLabel = type === 'MAINTENANCE' ? '유지보수' : '프로젝트';

  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchDetail = async () => {
      if (!id) {
        setError('작업 및 이슈이력 ID가 없습니다.');
        return;
      }

      setLoading(true);
      setError('');

      try {
        const res = await fetch(endpointFor(type, id));
        const result = await res.json();

        if (!res.ok || !result.success) {
          throw new Error(result.message || '상세 조회에 실패했습니다.');
        }

        setDetail(result.data);
      } catch (e) {
        setError(e.message || '상세 조회 중 오류가 발생했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchDetail();
  }, [id, type]);

  return (
    <div className="min-h-screen bg-slate-100 text-slate-900">
      <header className="border-b border-slate-800 bg-slate-900 px-5 py-4 text-white">
        <div className="flex items-center justify-between gap-4">
          <div>
            <div className="text-sm text-slate-300">작업 및 이슈이력</div>
            <h1 className="mt-1 text-xl font-bold">{typeLabel} 상세보기</h1>
          </div>

          <button
            type="button"
            onClick={() => window.close()}
            className="rounded-lg border border-slate-600 px-3 py-2 text-sm text-slate-100 hover:bg-slate-800"
          >
            창 닫기
          </button>
        </div>
      </header>

      <main className="p-5">
        {loading && (
          <div className="rounded-xl border border-slate-200 bg-white px-4 py-10 text-center text-slate-500 shadow-sm">
            상세 정보를 불러오는 중입니다.
          </div>
        )}

        {!loading && error && (
          <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-4 text-sm text-red-700">
            {error}
          </div>
        )}

        {!loading && !error && detail && (
          <div className="space-y-4">
            <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="text-2xl font-semibold text-slate-900">
                  {type === 'MAINTENANCE' ? detail.maintenanceName : detail.clientName}
                </h2>
                <span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-bold text-slate-700">
                  {typeLabel}
                </span>
              </div>

              <div className="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-4">
                <InfoBox title="사이트 코드" value={detail.siteCode} />
                <InfoBox title="영업 담당" value={detail.salesRep} />
                <InfoBox title="수행 인원" value={detail.executors || [detail.mainDev, detail.subDev].filter(Boolean).join(', ')} />
                <InfoBox title="방문" value={detail.visits} />
                <InfoBox title="MD" value={detail.md} />
                <InfoBox title="등록일" value={formatDateTime(detail.createdAt)} />
              </div>
            </section>

            {type === 'PROJECT' ? <ProjectDetail detail={detail} /> : <MaintenanceDetail detail={detail} />}
          </div>
        )}
      </main>
    </div>
  );
}

function ProjectDetail({ detail }) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="grid grid-cols-1 gap-4">
        <DetailBlock title="범위" value={detail.scope} />
        <DetailBlock title="위치" value={detail.location} />
        <DetailBlock title="시작일" value={detail.startDate} />
        <DetailBlock title="프로젝트 규모" value={detail.projectScale} />
        <DetailBlock title="진행 내역" value={detail.progressLogs} />
        <DetailBlock title="잔여 이슈" value={detail.remainingIssues} />
      </div>
    </section>
  );
}

function MaintenanceDetail({ detail }) {
  const inspectionDates = detail.inspectionDates || {};
  const inspectionText = Object.entries(inspectionDates)
    .map(([label, date]) => `${label}: ${date}`)
    .join('\n');

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="grid grid-cols-1 gap-4">
        <DetailBlock title="버전" value={detail.version} />
        <DetailBlock title="계약 기간" value={[detail.contractStart, detail.contractEnd].filter(Boolean).join(' ~ ')} />
        <DetailBlock title="지역" value={detail.region} />
        <DetailBlock title="정기점검 일정" value={inspectionText} />
        <DetailBlock title="진행 내역 / 이슈" value={detail.progressIssues} />
        <DetailBlock title="비고" value={detail.remarks} />
      </div>
    </section>
  );
}
