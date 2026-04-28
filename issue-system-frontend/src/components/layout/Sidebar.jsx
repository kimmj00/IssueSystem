import React from 'react';

// 좌측 메뉴
// activeMenu 값에 따라 선택된 메뉴 스타일을 바꿉니다.
export default function Sidebar({ activeMenu, setActiveMenu }) {
  const menus = [
    { key: 'GLOBAL_SEARCH', label: '통합 검색' },
    { key: 'ISSUE', label: '이슈관리 시스템' },
    { key: 'KNOWLEDGE', label: '지식공유' },
  ];

  return (
    <aside className="w-[180px] shrink-0 border-r border-slate-200 bg-white px-3 py-6">
      <div className="mb-8 px-1">
        <div className="text-sm font-bold text-slate-900">Issue System</div>
        <div className="mt-1 text-xs text-slate-500">이슈 관리 / 지식 공유</div>
      </div>

      <nav className="space-y-2">
        {menus.map((menu) => (
          <button
            key={menu.key}
            type="button"
            onClick={() => setActiveMenu(menu.key)}
            className={`w-full rounded-lg px-3 py-2.5 text-left text-sm font-medium transition ${
              activeMenu === menu.key
                ? 'bg-slate-900 text-white'
                : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            {menu.label}
          </button>
        ))}
      </nav>
    </aside>
  );
}
