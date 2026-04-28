import React, { useEffect, useMemo, useState } from 'react';
import Badge from '../components/common/Badge';
import DetailBlock from '../components/common/DetailBlock';
import { API_BASE } from '../constants/issueOptions';

// 이슈 상세보기 새 창 전용 페이지
// URL 예시: /?popup=issue-detail&id=1184
export default function IssueDetailWindow() {
  const issueId = useMemo(() => {
    const params = new URLSearchParams(window.location.search);
    return params.get('id');
  }, []);

  const [issue, setIssue] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchDetail = async () => {
      if (!issueId) {
        setError('이슈 ID가 없습니다.');
        return;
      }

      setLoading(true);
      setError('');

      try {
        const res = await fetch(`${API_BASE}/api/issue-cases/${issueId}`);
        const result = await res.json();

        if (!res.ok || !result.success) {
          throw new Error(result.message || '상세 조회에 실패했습니다.');
        }

        setIssue(result.data);
      } catch (e) {
        setError(e.message || '상세 조회 중 오류가 발생했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchDetail();
  }, [issueId]);

  return (
    <div className="min-h-screen bg-slate-100 text-slate-900">
      <header className="border-b border-slate-800 bg-slate-900 px-5 py-4 text-white">
        <div className="flex items-center justify-between gap-4">
          <div>
            <div className="text-sm text-slate-300">이슈관리 시스템</div>
            <h1 className="mt-1 text-xl font-bold">이슈 상세보기</h1>
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

        {!loading && !error && issue && (
          <div className="space-y-4">
            <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="text-2xl font-semibold text-slate-900">{issue.title}</h2>
                <Badge>{issue.infraType}</Badge>
                <Badge>{issue.status}</Badge>
              </div>

              <div className="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-4">
                <InfoBox title="ID" value={issue.id} />
                <InfoBox title="시스템명" value={issue.systemName} />
                <InfoBox title="고객사" value={issue.customerName} />
                <InfoBox title="구분" value={issue.category} />
                <InfoBox title="DB버전" value={issue.versionInfo} />
                <InfoBox title="배포 버전" value={issue.deploymentVersion} />
                <InfoBox title="작성자" value={issue.authorName} />
                <InfoBox title="작성일" value={formatDateTime(issue.createdAt)} />
              </div>
            </section>

            <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="grid grid-cols-1 gap-4">
                <DetailBlock title="증상 요약" value={issue.symptomSummary} />
                <DetailBlock title="증상 상세" value={issue.symptomDetail} />
                <DetailBlock title="원인" value={issue.causeDetail} />
                <DetailBlock title="조치 내용" value={issue.actionDetail} />
                <DetailBlock title="태그" value={issue.tags} />
              </div>
            </section>
          </div>
        )}
      </main>
    </div>
  );
}

function InfoBox({ title, value }) {
  return (
    <div className="rounded-lg bg-slate-50 px-3 py-3">
      <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">{title}</div>
      <div className="mt-1 break-words text-sm text-slate-900">{value || '-'}</div>
    </div>
  );
}

function formatDateTime(value) {
  if (!value) return '-';

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleString('ko-KR');
}
