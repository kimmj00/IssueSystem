import React from 'react';
import SectionCard from '../components/common/SectionCard';

// 지식공유 임시 화면
// 지식공유 게시판 또는 문서 목록 기능은 이후 이 파일에서 확장합니다.
export default function KnowledgePage() {
  return (
    <SectionCard title="지식공유" description="운영 지식과 해결 사례를 공유하는 화면입니다.">
      <div className="rounded-xl border border-dashed border-slate-300 px-4 py-10 text-center text-slate-500">
        지식공유 화면은 추후 구성 예정입니다.
      </div>
    </SectionCard>
  );
}
