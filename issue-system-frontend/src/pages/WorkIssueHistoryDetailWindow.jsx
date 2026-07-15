import React, { useEffect, useMemo, useState } from 'react';
import { API_BASE } from '../constants/patchHistoryOptions';

const text = (value) => (value == null ? '' : String(value).trim());
const lines = (value) => text(value)
  .split('\n')
  .map((line) => line.replace(/^[\s\-–·>▶□]+/, '').trim())
  .filter(Boolean);
const number = (value) => {
  const numeric = Number(value || 0);
  return Number.isFinite(numeric) ? numeric : 0;
};
const formatMetric = (value) => {
  const numeric = number(value);
  return Number.isInteger(numeric) ? String(numeric) : numeric.toFixed(1).replace(/\.0$/, '');
};
const maintenanceSupportScope = (item) => [
  ['SMS', text(item.smsStatus)],
  ['NMS', text(item.nmsStatus)],
]
  .filter(([, status]) => status && status.toUpperCase() !== 'X')
  .map(([name, status]) => `${name}(${status})`)
  .join('\n') || '-';

function endpointFor(type, id) {
  return type === 'MAINTENANCE'
    ? `${API_BASE}/api/work-issue-histories/maintenance/${id}`
    : `${API_BASE}/api/work-issue-histories/projects/${id}`;
}

function splitPeople(value) {
  return text(value).split(/[,/·\n]/).map((item) => item.trim()).filter(Boolean);
}

function parseTimeline(value, fallbackExecutor = '', createdAt = '') {
  const entries = [];
  let current = null;
  const sortKey = (date) => {
    const match = text(date).match(/(\d{1,2})[/.](\d{1,2})/);
    return match ? Number(match[1]) * 100 + Number(match[2]) : 0;
  };

  text(value).split('\n').map((line) => line.trim()).filter(Boolean).forEach((line) => {
    const header = line.match(/^(\d{1,2}[/.]\d{1,2})(?:\s*[~-]\s*(\d{1,2}(?:[/.]\d{1,2})?))?\s*(?:\(([^)]+)\))?\s*(.*)$/);

    if (header) {
      if (current) entries.push(current);
      current = {
        date: header[2] ? `${header[1]}~${header[2]}` : header[1],
        sortKey: sortKey(header[1]),
        type: text(header[3]),
        executor: fallbackExecutor,
        content: header[4] ? [header[4].replace(/^[\s\-–·>▶□]+/, '').trim()] : [],
      };
      return;
    }

    if (!current) current = { date: '', sortKey: 0, type: '', executor: fallbackExecutor, content: [] };
    current.content.push(line.replace(/^[\s\-–·>▶□]+/, '').trim());
  });

  if (current) entries.push(current);
  const normalizedEntries = entries
    .map((entry) => ({
      ...entry,
      content: entry.content
        .filter(Boolean)
        .map((item) => (/\[?프로젝트\s*종료\]?/.test(item) ? '[프로젝트 종료]' : item)),
      projectClosed: entry.content.some((item) => /\[프로젝트\s*종료\]|\[종료\]/.test(item)),
    }))
    .filter((entry) => entry.date || entry.content.length);

  let year = Number(text(createdAt).slice(0, 4)) || new Date().getFullYear();
  let previousMonthDay = null;

  return normalizedEntries.map((entry) => {
    if (!entry.sortKey) return entry;
    if (previousMonthDay !== null && entry.sortKey > previousMonthDay) year -= 1;
    previousMonthDay = entry.sortKey;
    return { ...entry, year, sortKey: year * 10000 + entry.sortKey };
  });
}

function SectionTitle({ children, amber = false }) {
  return (
    <div className={`mb-4 flex items-center gap-2 text-sm font-bold ${amber ? 'text-amber-700' : 'text-blue-700'}`}>
      <span aria-hidden="true">⌁</span>
      <span>{children}</span>
    </div>
  );
}

function Timeline({ entries, maintenance }) {
  return (
    <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
      {entries.length ? entries.map((entry, index) => (
        <div key={`${entry.date}-${index}`} className="border-b border-slate-100 last:border-0">
          {(entry.date || entry.type || entry.executor) && (
            <div className={`flex flex-wrap items-center gap-3 px-5 py-3 text-xs font-bold ${maintenance ? 'bg-teal-50 text-teal-900' : 'bg-blue-50 text-blue-900'}`}>
              {entry.date && <span className="font-mono text-sm">{entry.date}</span>}
              {entry.type && <span className={`rounded-full px-2 py-1 text-[11px] ${maintenance ? 'bg-teal-100 text-teal-700' : 'bg-blue-100 text-blue-700'}`}>{entry.type}</span>}
              {entry.executor && <span className="border-l border-slate-200 pl-3 text-slate-600">{entry.executor}</span>}
            </div>
          )}
          <ul className="space-y-3 px-5 py-5 text-sm text-slate-700">
            {entry.content.map((item, itemIndex) => (
              <li key={itemIndex} className="flex gap-3">
                <span className={`mt-2 h-2 w-2 shrink-0 rounded-full ${maintenance ? 'bg-teal-500' : 'bg-blue-500'}`} />
                <span>{item}</span>
              </li>
            ))}
          </ul>
        </div>
      )) : <div className="px-5 py-8 text-sm text-slate-500">진행 내역이 없습니다.</div>}
    </div>
  );
}

function StatBox({ title, children }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="mb-3 text-xs font-bold text-slate-500">{title}</div>
      {children}
    </div>
  );
}

function InspectionStatus({ dates }) {
  const years = Object.entries(dates || {}).reduce((result, [monthLabel, value]) => {
    const match = text(value).match(/^(\d{4})-(\d{2})-(\d{2})$/);
    if (!match) return result;

    const [, year, month, day] = match;
    const items = result.get(year) || [];
    items.push({ monthLabel, month, day, sortKey: Number(month) * 100 + Number(day) });
    result.set(year, items);
    return result;
  }, new Map());

  const yearEntries = [...years.entries()]
    .sort(([yearA], [yearB]) => Number(yearB) - Number(yearA))
    .map(([year, items]) => [year, items.sort((a, b) => a.sortKey - b.sortKey)]);

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="mb-4 text-xs font-bold text-slate-500">정기점검 현황</div>
      <div className="space-y-3">
        {yearEntries.length ? yearEntries.map(([year, items]) => (
          <div key={year} className="flex items-start gap-4 text-sm">
            <span className="w-14 shrink-0 font-black text-slate-700">{year}년</span>
            <span className="font-mono font-bold leading-6 text-teal-700">
              {items.map((item) => `${item.month}/${item.day}`).join(', ')}
            </span>
          </div>
        )) : (
          <div className="text-sm text-slate-500">등록된 정기점검 일정이 없습니다.</div>
        )}
      </div>
    </section>
  );
}

export default function WorkIssueHistoryDetailWindow({ headerAction = null }) {
  const params = useMemo(() => new URLSearchParams(window.location.search), []);
  const ids = useMemo(() => {
    const value = params.get('ids') || params.get('id') || '';
    return [...new Set(value.split(',').map((item) => item.trim()).filter(Boolean))];
  }, [params]);
  const type = params.get('type') === 'MAINTENANCE' ? 'MAINTENANCE' : 'PROJECT';
  const handleClose = () => {
    if (params.get('embedded') === '1' && window.parent && window.parent !== window) {
      window.parent.postMessage(
        {
          type: 'issue-system:close-split-screen',
          index: Number(params.get('splitIndex')),
        },
        window.location.origin
      );
      return;
    }

    window.close();
  };

  const [details, setDetails] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (ids.length === 0) {
      setError('작업 및 이슈이력 ID가 없습니다.');
      setLoading(false);
      return;
    }

    Promise.all(ids.map((id) => fetch(endpointFor(type, id)).then(async (response) => {
      const result = await response.json();
      if (!response.ok || result.success === false) throw new Error(result.message || '상세 조회에 실패했습니다.');
      return result.data ?? result;
    })))
      .then(setDetails)
      .catch((e) => setError(e.message || '상세 조회 중 오류가 발생했습니다.'))
      .finally(() => setLoading(false));
  }, [ids, type]);

  if (loading) return <StatusBox>상세 정보를 불러오는 중입니다.</StatusBox>;
  if (error) return <StatusBox error>{error}</StatusBox>;
  if (!details.length) return <StatusBox>상세 정보를 찾을 수 없습니다.</StatusBox>;

  const maintenance = type === 'MAINTENANCE';
  const detail = details[0];
  const title = maintenance ? detail.maintenanceName : detail.clientName;
  const executors = [...new Set(details.flatMap((item) => maintenance
    ? splitPeople(item.mainDev)
    : splitPeople(item.executors)))];
  const executorText = maintenance
    ? text(detail.mainDev) || '-'
    : executors.join(', ') || '-';
  const maintenanceOwners = splitPeople(detail.mainDev);
  const personStats = new Map();

  details.forEach((item) => {
    const authors = maintenance
      ? splitPeople([item.mainDev, item.subDev].filter(Boolean).join(','))
      : splitPeople(item.executors);

    authors.forEach((person) => {
      const current = personStats.get(person) || { visits: 0, md: 0 };
      personStats.set(person, {
        visits: current.visits + number(item.visits),
        md: current.md + number(item.md),
      });
    });
  });
  const issues = [...new Set(details.flatMap((item) => lines(maintenance ? item.remarks : item.remainingIssues)))];
  const timeline = details.flatMap((item) => {
    const authors = maintenance
      ? splitPeople(item.mainDev)
      : splitPeople(item.executors);
    return parseTimeline(maintenance ? item.progressIssues : item.progressLogs, authors.join(', '), item.createdAt);
  }).sort((a, b) => Number(b.projectClosed) - Number(a.projectClosed) || b.sortKey - a.sortKey);
  const scope = maintenance
    ? maintenanceSupportScope(detail)
    : [...new Set(details.map((item) => text(item.scope)).filter(Boolean))].join('\n') || '-';

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white px-6 py-5">
        <div className="grid grid-cols-[40px_minmax(300px,1.3fr)_minmax(0,1.7fr)_24px] items-center gap-2 text-sm">
          <span className="font-mono text-slate-500">{detail.no || detail.rowNo || ids[0]}</span>
          <div className="min-w-0">
            <div className="flex items-center gap-2">
             <h1 className="truncate text-lg font-bold">{title || '-'}</h1>
             <span className="rounded-md border border-slate-200 px-1.5 py-1 text-slate-500">↗</span>
             {headerAction}
            </div>
            <div className="mt-1 text-xs text-slate-500">{maintenance ? '유지보수' : text(detail.projectScale) || '-'}</div>
          </div>

          <div
            className="grid min-w-0 w-full grid-cols-4 items-center text-center"
            style={{ transform: 'translateX(3ch)' }}
          >
            <span className="w-full min-w-0 break-words">{text(detail.siteCode) || '-'}</span>
            <span className="w-full min-w-0 break-words">{executorText}</span>
            <span className="w-full min-w-0 break-words">{maintenance ? text(detail.contractEnd) || '-' : text(detail.startDate) || '-'}</span>
            <span className="w-full min-w-0 break-words">{maintenance ? text(detail.cycle) || '-' : text(detail.projectScale) || '-'}</span>
          </div>
          <button type="button" onClick={handleClose} aria-label="창 닫기" className="text-xl text-slate-400 hover:text-slate-700">×</button>
        </div>
      </header>

      <main className="grid gap-8 px-7 py-9 lg:grid-cols-2">
        <section>
          <SectionTitle>{maintenance ? '유지보수 진행내역 및 이슈' : '금주 실적 및 진행 내역 (누적)'}</SectionTitle>
          <Timeline entries={timeline} maintenance={maintenance} />
          {!maintenance && detail.startDate && (
            <div className="mt-4 inline-flex rounded-md border border-blue-200 bg-blue-50 px-3 py-1 text-xs font-bold text-blue-700">Start: {detail.startDate}</div>
          )}
        </section>

        <section className="space-y-7">
          <div>
            <SectionTitle amber>잔여 사항 및 이슈 (주의요망)</SectionTitle>
            <div className="min-h-36 rounded-2xl border border-amber-300 bg-amber-50 px-6 py-6 text-sm leading-8 text-amber-800 shadow-sm">
              {issues.length ? issues.map((item, index) => <div key={index}>□&nbsp; {item}</div>) : <div>잔여 사항 및 이슈가 없습니다.</div>}
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <StatBox title={maintenance ? '지원 범위' : '구축 범위'}>
              <div className="whitespace-pre-wrap font-bold leading-7">{scope}</div>
            </StatBox>
            <StatBox title="인원별 지원 횟수 / MD">
              <div className="space-y-2">
                {!maintenance && executors.length ? executors.map((person) => {
                  const stats = personStats.get(person) || { visits: 0, md: 0 };
                  return (
                    <div key={person} className="flex items-center justify-between gap-4">
                      <span className="font-bold text-slate-700">{person}</span>
                      <span className="font-mono font-black">
                        {formatMetric(stats.visits)}회&nbsp; / &nbsp;{formatMetric(stats.md)}MD
                      </span>
                    </div>
                  );
                }) : null}
                {maintenance && maintenanceOwners.length ? maintenanceOwners.map((person) => (
                    <div key={person} className="flex items-center justify-between gap-4">
                      <span className="font-bold text-slate-700">{person}</span>
                      <span className="font-mono font-black">
                        {formatMetric(detail.visits)}회&nbsp; / &nbsp;{formatMetric(detail.md)}MD
                      </span>
                    </div>
                )) : null}
                {maintenance && !maintenanceOwners.length ? <div className="text-slate-500">-</div> : null}
                {!maintenance && !executors.length ? <div className="text-slate-500">-</div> : null}
              </div>
            </StatBox>
          </div>

          {maintenance ? <InspectionStatus dates={detail.inspectionDates} /> : null}
        </section>
      </main>
    </div>
  );
}

function StatusBox({ children, error = false }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 p-6">
      <div className={`rounded-xl border px-8 py-6 text-sm shadow-sm ${error ? 'border-red-200 bg-red-50 text-red-700' : 'border-slate-200 bg-white text-slate-500'}`}>{children}</div>
    </div>
  );
}
