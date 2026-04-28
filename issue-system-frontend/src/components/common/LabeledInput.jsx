import React from 'react';

// 라벨 + 입력 영역 공통 컴포넌트
// compact=true이면 검색 영역처럼 작게 표시합니다.
export default function LabeledInput({ label, children, compact = false }) {
  return (
    <label className="block min-w-0">
      <div className={`${compact ? 'mb-1 text-xs' : 'mb-2 text-sm'} font-medium text-slate-700`}>
        {label}
      </div>
      {children}
    </label>
  );
}
