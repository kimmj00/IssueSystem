import React from 'react';
import { highlightText } from '../../utils/highlightText';

// 상세보기 모달에서 긴 텍스트를 보여주는 블록
export default function DetailBlock({ title, value, keyword = '' }) {
  const activeKeyword = keyword
    || (typeof window !== 'undefined'
      ? new URLSearchParams(window.location.search).get('keyword') || ''
      : '');

  return (
    <div>
      <div className="mb-2 text-sm font-semibold text-slate-700">{title}</div>
      <div className="min-h-14 whitespace-pre-wrap rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-800">
        {highlightText(value || '-', activeKeyword)}
      </div>
    </div>
  );
}
