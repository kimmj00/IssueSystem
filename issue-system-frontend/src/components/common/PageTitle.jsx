import React from 'react';

/**
 * 페이지 상단 제목 영역 공통 컴포넌트
 *
 * Tailwind gap/margin 클래스가 화면에 즉시 반영되지 않거나
 * 페이지마다 제목/설명 간격이 달라지는 문제를 피하기 위해,
 * 제목과 설명 사이 간격은 inline style로 명확하게 고정한다.
 */
export default function PageTitle({ title, description }) {
  return (
    <div
      className="mb-4"
      style={{
        display: 'flex',
        alignItems: 'baseline',
        columnGap: '48px',
        flexWrap: 'wrap',
      }}
    >
      <h1 className="shrink-0 text-3xl font-bold tracking-tight text-slate-900">
        {title}
      </h1>

      <p
        className="text-sm text-slate-500"
        style={{
          marginTop: '6px',
          paddingLeft: '24px',
          borderLeft: '1px solid #cbd5e1',
          lineHeight: '1.5',
        }}
      >
        {description}
      </p>
    </div>
  );
}
