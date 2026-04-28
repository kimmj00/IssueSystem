import React from 'react';

// 공통 카드 컴포넌트
// 검색, 목록, 임시 페이지 등 여러 화면에서 같은 박스 스타일을 재사용합니다.
export default function SectionCard({ title, description, children, className = '' }) {
  return (
    <section className={`rounded-2xl border border-slate-200 bg-white p-4 shadow-sm ${className}`}>
      {(title || description) && (
        <div className="mb-4">
          {title ? <h2 className="text-lg font-semibold text-slate-900">{title}</h2> : null}
          {description ? <p className="mt-1 text-sm text-slate-500">{description}</p> : null}
        </div>
      )}

      {children}
    </section>
  );
}
