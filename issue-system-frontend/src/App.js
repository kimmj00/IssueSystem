import React, { useState } from 'react';
import Sidebar from './components/layout/Sidebar';
import GlobalSearchPage from './pages/GlobalSearchPage';
import IssuePage from './pages/IssuePage';
import KnowledgePage from './pages/KnowledgePage';
import IssueDetailWindow from './pages/IssueDetailWindow';
import KnowledgeDetailWindow from './pages/KnowledgeDetailWindow';

const menuKeys = ['GLOBAL_SEARCH', 'ISSUE', 'KNOWLEDGE'];

// 현재 URL 또는 localStorage에서 마지막 메뉴를 읽어온다.
function getInitialMenu() {
  const params = new URLSearchParams(window.location.search);
  const menuFromUrl = params.get('menu');

  if (menuKeys.includes(menuFromUrl)) {
    return menuFromUrl;
  }

  const menuFromStorage = localStorage.getItem('activeMenu');

  if (menuKeys.includes(menuFromStorage)) {
    return menuFromStorage;
  }

  return 'ISSUE';
}

// 앱 최상위 컴포넌트
// 화면별 세부 로직은 pages 디렉터리로 분리했습니다.
export default function App() {
  // 상세보기 새 창으로 열린 경우에는 좌측 메뉴 없는 전용 화면을 렌더링합니다.
  const params = new URLSearchParams(window.location.search);
  const popupType = params.get('popup');

  // 현재 선택된 좌측 메뉴
  // GLOBAL_SEARCH: 통합 검색
  // ISSUE: 이슈관리 시스템
  // KNOWLEDGE: 지식공유
  const [activeMenu, setActiveMenu] = useState(getInitialMenu);

  const handleMenuChange = (menuKey) => {
    setActiveMenu(menuKey);
    localStorage.setItem('activeMenu', menuKey);

    const params = new URLSearchParams(window.location.search);

    // 상세보기 새창용 popup 파라미터가 있을 때는 건드리지 않는다.
    // 일반 메인 화면에서만 menu 파라미터를 갱신한다.
    if (!params.get('popup')) {
      params.set('menu', menuKey);

      const nextUrl = `${window.location.pathname}?${params.toString()}`;
      window.history.replaceState(null, '', nextUrl);
    }
  };

  if (popupType === 'issue-detail') {
    return <IssueDetailWindow />;
  }

  if (popupType === 'knowledge-detail') {
    return <KnowledgeDetailWindow />;
  }

  return (
    <div className="min-h-screen w-full overflow-x-hidden bg-slate-50 text-slate-900">
      <div className="flex min-h-screen w-full">
        <Sidebar activeMenu={activeMenu} setActiveMenu={handleMenuChange} />

        <main className="min-w-0 flex-1 px-4 py-5 sm:px-6 lg:px-6">
          {activeMenu === 'GLOBAL_SEARCH' && <GlobalSearchPage />}
          {activeMenu === 'ISSUE' && <IssuePage />}
          {activeMenu === 'KNOWLEDGE' && <KnowledgePage />}
        </main>
      </div>
    </div>
  );
}
