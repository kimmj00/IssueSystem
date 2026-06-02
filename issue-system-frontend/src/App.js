import React, { useState } from 'react';
import Sidebar from './components/layout/Sidebar';
import GlobalSearchPage from './pages/GlobalSearchPage';
import PatchHistoryPage from './pages/PatchHistoryPage';
import KnowledgePage from './pages/KnowledgePage';
import WorkIssueHistoryPage from './pages/WorkIssueHistoryPage';
import PatchHistoryDetailWindow from './pages/PatchHistoryDetailWindow';
import KnowledgeDetailWindow from './pages/KnowledgeDetailWindow';
import WorkIssueHistoryDetailWindow from './pages/WorkIssueHistoryDetailWindow';

// 메뉴 키는 화면 의미에 맞게 정리했습니다.
// 기존 ISSUE 메뉴는 실제 패치리스트 기능이므로 PATCH_HISTORY로 변경했습니다.
// 기존 PATCH_HISTORY 빈 페이지는 작업 및 이슈이력 메뉴로 변경했습니다.
const menuKeys = ['GLOBAL_SEARCH', 'PATCH_HISTORY', 'KNOWLEDGE', 'WORK_ISSUE_HISTORY'];

const DEFAULT_MENU = 'GLOBAL_SEARCH'
// 현재 URL 또는 localStorage에서 마지막 메뉴를 읽어온다.
function getInitialMenu() {
  const params = new URLSearchParams(window.location.search);
  const menuFromUrl = params.get('menu');

  if (menuKeys.includes(menuFromUrl)) {
    return menuFromUrl;
  }

  // 기본 진입 화면은 실제 데이터 기능이 있는 패치이력으로 둡니다.
  return DEFAULT_MENU;
}

// 앱 최상위 컴포넌트
// 상세보기 새 창과 일반 메인 화면 레이아웃을 분리해서 처리한다.
export default function App() {
  // 상세보기 새 창으로 열린 경우에는 상단 메뉴 없는 전용 화면을 렌더링한다.
  const params = new URLSearchParams(window.location.search);
  const popupType = params.get('popup');

  // 현재 선택된 메뉴
  // GLOBAL_SEARCH: 통합 검색
  // PATCH_HISTORY: 패치이력
  // KNOWLEDGE: 지식공유
  // WORK_ISSUE_HISTORY: 작업 및 이슈이력
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

  if (popupType === 'patch-history-detail') {
    return <PatchHistoryDetailWindow />;
  }

  // 기존에 열린 /?popup=issue-detail 링크도 깨지지 않도록 호환 처리합니다.
  if (popupType === 'issue-detail') {
    return <PatchHistoryDetailWindow />;
  }

  if (popupType === 'knowledge-detail') {
    return <KnowledgeDetailWindow />;
  }

  if (popupType === 'work-issue-history-detail') {
    return <WorkIssueHistoryDetailWindow />;
  }

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      {/* 기존 Sidebar 컴포넌트 파일명을 유지하되, 실제 UI는 상단 헤더 메뉴로 사용한다. */}
      <Sidebar activeMenu={activeMenu} setActiveMenu={handleMenuChange} />

      {/* 화면이 너무 넓어 보이지 않도록 최대 폭을 제한하고 중앙 정렬한다. */}
      <main className="mx-auto w-full max-w-[1720px] px-4 py-6 sm:px-6 lg:px-8">
        {activeMenu === 'GLOBAL_SEARCH' && <GlobalSearchPage />}
        {activeMenu === 'PATCH_HISTORY' && <PatchHistoryPage />}
        {activeMenu === 'KNOWLEDGE' && <KnowledgePage />}
        {activeMenu === 'WORK_ISSUE_HISTORY' && <WorkIssueHistoryPage />}
      </main>
    </div>
  );
}
