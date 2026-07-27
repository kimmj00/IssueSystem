import React from 'react';

// 상단 메뉴 컴포넌트
// 파일명은 기존 import 경로를 유지하기 위해 Sidebar.jsx 그대로 사용합니다.
export default function Sidebar({ activeMenu, setActiveMenu, userName, userRole, onLogout }) {
  const menus = [
    { key: 'GLOBAL_SEARCH', label: '통합 검색', description: '전체 검색' },
    // 기존 패치이력 메뉴에 들어 있던 실제 기능은 패치리스트 조회/업로드였으므로 패치이력으로 표시합니다.
    { key: 'PATCH_HISTORY', label: '패치이력', description: '패치리스트 조회' },
    { key: 'KNOWLEDGE', label: '지식공유', description: '운영 지식' },
    // 기존 패치이력 빈 페이지는 작업 및 이슈이력 메뉴로 표시합니다.
    { key: 'WORK_ISSUE_HISTORY', label: '작업 및 이슈이력', description: '프로젝트 · 유지보수' },
    ...(userRole === 'ADMIN' ? [{ key: 'ADMIN_ACCOUNTS', label: '계정 관리', description: '가입 계정 조회' }] : []),
  ];

  return (
    <header className="sticky top-0 z-50 border-b border-sky-100 bg-sky-50 text-slate-900 shadow-sm">
      <div className="mx-auto flex min-h-[72px] w-full max-w-[1720px] items-center px-4 sm:px-6 lg:px-8">
        {/* 좌측 브랜드 영역 */}
        <button
          type="button"
          onClick={() => setActiveMenu('GLOBAL_SEARCH')}
          className="flex min-w-[168px] shrink-0 items-center gap-3 text-left"
        >
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-sky-600 text-sm font-black text-white shadow-sm">
            TC
          </div>

          <div className="truncate text-base font-bold tracking-wide text-slate-900">
            TC Bank
          </div>
        </button>

        {/* 
          상단 메뉴 영역
          - 우측 끝 배치가 어색해서 브랜드 오른쪽으로 이동
          - 브랜드와 너무 붙지 않도록 ml-10 간격 적용
          - 남는 공간은 오른쪽 빈 영역으로 유지
        */}
        <nav className="ml-6 flex shrink-0 items-center gap-1 rounded-2xl border border-sky-100 bg-white/80 p-1 shadow-sm">
          {menus.map((menu) => {
            const active = activeMenu === menu.key;

            return (
              <button
                key={menu.key}
                type="button"
                onClick={() => setActiveMenu(menu.key)}
                /*
                 * 선택된 메뉴는 흰색 배경이므로 텍스트 색상을 직접 지정합니다.
                 * 부모 text 색상 상속을 피해서 선택 상태에서도 글자가 선명하게 보이도록 처리합니다.
                 */
                className={`group rounded-xl border px-3 py-2 text-left transition ${
                  active
                    ? 'border-sky-200 bg-sky-100 shadow-sm'
                    : 'border-transparent text-slate-600 hover:bg-sky-50 hover:text-sky-800'
                }`}
              >
                <div
                  className={`text-sm font-bold leading-5 ${
                    active ? 'text-sky-800' : 'text-slate-700 group-hover:text-sky-800'
                  }`}
                >
                  {menu.label}
                </div>

                <div
                  className={`hidden text-[11px] leading-4 xl:block ${
                    active ? 'text-sky-600' : 'text-slate-500 group-hover:text-sky-600'
                  }`}
                >
                  {menu.description}
                </div>
              </button>
            );
          })}
        </nav>

        <div className="ml-auto flex items-center gap-3 pl-4">
          <span className="hidden h-9 items-center gap-1.5 text-sm font-bold leading-none text-slate-600 sm:flex">
            <svg
              aria-hidden="true"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              className="h-5 w-5 shrink-0 text-sky-600"
            >
              <circle cx="12" cy="8" r="3.5" />
              <path d="M5.5 19c.7-3.3 3-5 6.5-5s5.8 1.7 6.5 5" strokeLinecap="round" />
            </svg>
            <span className="inline-flex h-5 items-center">{userName}님</span>
          </span>
          <button
            type="button"
            onClick={onLogout}
            className="rounded-lg border border-sky-200 bg-white px-3 py-2 text-xs font-semibold text-slate-600 transition hover:border-sky-300 hover:bg-sky-50 hover:text-sky-700"
          >
            로그아웃
          </button>
        </div>
      </div>
    </header>
  );
}
