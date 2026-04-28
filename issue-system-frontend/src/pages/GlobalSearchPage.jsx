import React from 'react';
import SectionCard from '../components/common/SectionCard';

// 통합 검색 임시 화면
// 실제 통합 검색 기능은 이후 API가 생기면 이 파일에서 확장합니다.
export default function GlobalSearchPage() {
  return (
    <SectionCard title="통합 검색" description="전체 지식과 이슈를 통합 검색하는 화면입니다.">
      <div className="rounded-xl border border-dashed border-slate-300 px-4 py-10 text-center text-slate-500">
        통합 검색 화면은 추후 구성 예정입니다.
      </div>
    </SectionCard>
  );
}
