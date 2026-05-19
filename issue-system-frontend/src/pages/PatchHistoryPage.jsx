import React from 'react';
import SectionCard from '../components/common/SectionCard';

// 패치이력 페이지
// 아직 기능 구상 전이므로 화면 틀만 먼저 생성합니다.
export default function PatchHistoryPage() {
  return (
    <>
      <div className="mb-4 flex items-baseline">
        <h1 className="shrink-0 text-3xl font-bold tracking-tight text-slate-900">
          패치이력
        </h1>
        <p className="ml-16 border-l border-slate-300 pl-8 text-sm text-slate-500">
          패치이력 기능은 아직 구상 전입니다.
        </p>
      </div>

      <SectionCard title="패치이력" description="추후 패치이력 목록과 검색 기능이 들어갈 예정입니다.">
        <div className="rounded-xl border border-dashed border-slate-300 px-4 py-16 text-center text-sm text-slate-500">
          아직 등록된 화면 구성이 없습니다.
        </div>
      </SectionCard>
    </>
  );
}
