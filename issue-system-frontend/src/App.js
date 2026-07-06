import React, { useEffect, useRef, useState } from 'react';
import Sidebar from './components/layout/Sidebar';
import GlobalSearchPage from './pages/GlobalSearchPage';
import PatchHistoryPage from './pages/PatchHistoryPage';
import KnowledgePage from './pages/KnowledgePage';
import WorkIssueHistoryPage from './pages/WorkIssueHistoryPage';
import PatchHistoryDetailWindow from './pages/PatchHistoryDetailWindow';
import KnowledgeDetailWindow from './pages/KnowledgeDetailWindow';
import WorkIssueHistoryDetailWindow from './pages/WorkIssueHistoryDetailWindow';
import SplitScreenEdgePanel from './components/layout/SplitScreenEdgePanel';
import SaveScreenButton from './components/common/SaveScreenButton';
import {
  SAVED_SCREEN_DRAG_END_EVENT,
  SAVED_SCREEN_DRAG_START_EVENT,
} from './utils/savedScreens';

// 메뉴 키는 화면 의미에 맞게 정리했습니다.
// 기존 ISSUE 메뉴는 실제 패치리스트 기능이므로 PATCH_HISTORY로 변경했습니다.
// 기존 PATCH_HISTORY 빈 페이지는 작업 및 이슈이력 메뉴로 변경했습니다.
const menuKeys = ['GLOBAL_SEARCH', 'PATCH_HISTORY', 'KNOWLEDGE', 'WORK_ISSUE_HISTORY'];

const DEFAULT_MENU = 'GLOBAL_SEARCH'
const SPLIT_SETTINGS_EVENT = 'issue-system:split-settings-change';
const SPLIT_CLOSE_MESSAGE = 'issue-system:close-split-screen';
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

function PopupLayout({ title, children }) {
  const url = `${window.location.origin}${window.location.pathname}${window.location.search}`;
  const saveAction = (
    <SaveScreenButton
      title={title}
      url={url}
      openMode="popup"
      fixed={false}
      buttonClassName="h-8 w-8 border-slate-600 bg-slate-900 text-slate-100 shadow-none hover:border-sky-300 hover:bg-slate-800 hover:text-sky-200"
    />
  );

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <main className="screen-capture-source">
        {React.cloneElement(children, { headerAction: saveAction })}
      </main>
    </div>
  );
}

function getSplitSettings() {
  return {
    enabled: localStorage.getItem('issue-system:split-enabled') === 'true',
    mode: localStorage.getItem('issue-system:split-mode') || '2',
  };
}

function buildEmbeddedUrl(url, index) {
  const nextUrl = new URL(url, window.location.href);
  if (['localhost', '127.0.0.1'].includes(nextUrl.hostname)) {
    nextUrl.protocol = window.location.protocol;
    nextUrl.host = window.location.host;
  }
  nextUrl.searchParams.set('embedded', '1');
  nextUrl.searchParams.set('splitIndex', String(index));
  return nextUrl.toString();
}

function isPopupScreen(screen) {
  if (!screen?.url) {
    return false;
  }

  return screen.openMode === 'popup' || new URL(screen.url, window.location.href).searchParams.has('popup');
}

function getDefaultSplitColumns(mode) {
  if (mode === '3') {
    return [33.33, 33.34, 33.33];
  }

  return [50, 50];
}

// 앱 최상위 컴포넌트
// 상세보기 새 창과 일반 메인 화면 레이아웃을 분리해서 처리한다.
export default function App() {
  // 상세보기 새 창으로 열린 경우에는 상단 메뉴 없는 전용 화면을 렌더링한다.
  const params = new URLSearchParams(window.location.search);
  const popupType = params.get('popup');
  const embedded = params.get('embedded') === '1';

  // 현재 선택된 메뉴
  // GLOBAL_SEARCH: 통합 검색
  // PATCH_HISTORY: 패치이력
  // KNOWLEDGE: 지식공유
  // WORK_ISSUE_HISTORY: 작업 및 이슈이력
  const [activeMenu, setActiveMenu] = useState(getInitialMenu);
  const [splitSettings, setSplitSettings] = useState(getSplitSettings);
  const [splitScreens, setSplitScreens] = useState([]);
  const [splitColumns, setSplitColumns] = useState([50, 50]);
  const [splitRows, setSplitRows] = useState([50, 50]);
  const [draggingSavedScreen, setDraggingSavedScreen] = useState(false);
  const splitContainerRef = useRef(null);

  useEffect(() => {
    const syncSplitSettings = () => {
      setSplitSettings(getSplitSettings());
    };

    window.addEventListener(SPLIT_SETTINGS_EVENT, syncSplitSettings);
    window.addEventListener('storage', syncSplitSettings);

    return () => {
      window.removeEventListener(SPLIT_SETTINGS_EVENT, syncSplitSettings);
      window.removeEventListener('storage', syncSplitSettings);
    };
  }, []);

  useEffect(() => {
    setSplitColumns(getDefaultSplitColumns(splitSettings.mode));
    setSplitRows([50, 50]);
  }, [splitSettings.enabled, splitSettings.mode]);

  useEffect(() => {
    const handleSplitCloseMessage = (event) => {
      if (event.origin !== window.location.origin || event.data?.type !== SPLIT_CLOSE_MESSAGE) {
        return;
      }

      const index = Number(event.data.index);
      if (!Number.isInteger(index)) {
        return;
      }

      setSplitScreens((prev) => {
        const next = [...prev];
        next[index] = null;
        return next;
      });
    };

    window.addEventListener('message', handleSplitCloseMessage);
    return () => window.removeEventListener('message', handleSplitCloseMessage);
  }, []);

  useEffect(() => {
    const clearDraggingState = () => setDraggingSavedScreen(false);
    const showDropOverlay = () => setDraggingSavedScreen(true);

    window.addEventListener(SAVED_SCREEN_DRAG_START_EVENT, showDropOverlay);
    window.addEventListener(SAVED_SCREEN_DRAG_END_EVENT, clearDraggingState);
    window.addEventListener('dragend', clearDraggingState);
    window.addEventListener('drop', clearDraggingState);

    return () => {
      window.removeEventListener(SAVED_SCREEN_DRAG_START_EVENT, showDropOverlay);
      window.removeEventListener(SAVED_SCREEN_DRAG_END_EVENT, clearDraggingState);
      window.removeEventListener('dragend', clearDraggingState);
      window.removeEventListener('drop', clearDraggingState);
    };
  }, []);

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
    if (embedded) {
      return <PatchHistoryDetailWindow />;
    }

    return (
      <PopupLayout title="패치이력 상세">
        <PatchHistoryDetailWindow />
      </PopupLayout>
    );
  }

  // 기존에 열린 /?popup=issue-detail 링크도 깨지지 않도록 호환 처리합니다.
  if (popupType === 'issue-detail') {
    if (embedded) {
      return <PatchHistoryDetailWindow />;
    }

    return (
      <PopupLayout title="패치이력 상세">
        <PatchHistoryDetailWindow />
      </PopupLayout>
    );
  }

  if (popupType === 'knowledge-detail') {
    if (embedded) {
      return <KnowledgeDetailWindow />;
    }

    return (
      <PopupLayout title="지식공유 상세">
        <KnowledgeDetailWindow />
      </PopupLayout>
    );
  }

  if (popupType === 'work-issue-history-detail') {
    if (embedded) {
      return <WorkIssueHistoryDetailWindow />;
    }

    return (
      <PopupLayout title="작업 및 이슈이력 상세">
        <WorkIssueHistoryDetailWindow />
      </PopupLayout>
    );
  }

  const renderActivePage = () => (
    <>
      {activeMenu === 'GLOBAL_SEARCH' && <GlobalSearchPage />}
      {activeMenu === 'PATCH_HISTORY' && <PatchHistoryPage />}
      {activeMenu === 'KNOWLEDGE' && <KnowledgePage />}
      {activeMenu === 'WORK_ISSUE_HISTORY' && <WorkIssueHistoryPage />}
    </>
  );

  if (embedded) {
    return (
      <main className="min-h-screen bg-slate-50 p-3 text-slate-900">
        {renderActivePage()}
      </main>
    );
  }

  const handleDropSplitScreen = (event, index) => {
    event.preventDefault();
    event.stopPropagation();
    setDraggingSavedScreen(false);

    try {
      const screen = JSON.parse(event.dataTransfer.getData('application/json'));
      if (!screen?.url) {
        return;
      }

      setSplitScreens((prev) => {
        const next = [...prev];
        next[index] = screen;
        return next;
      });
    } catch (e) {
      // 드래그 데이터가 저장 화면이 아니면 무시합니다.
    }
  };

  const handleSplitDragOver = (event) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'copy';

    if (!draggingSavedScreen) {
      setDraggingSavedScreen(true);
    }
  };

  const handleSplitDragLeave = (event) => {
    const container = splitContainerRef.current;
    if (container && event.relatedTarget instanceof Node && container.contains(event.relatedTarget)) {
      return;
    }

    setDraggingSavedScreen(false);
  };

  const handleCloseSplitScreen = (index) => {
    setSplitScreens((prev) => {
      const next = [...prev];
      next[index] = null;
      return next;
    });
  };

  const resizeAdjacent = (values, index, positionPercent) => {
    const minSize = 15;
    const before = values.slice(0, index).reduce((sum, value) => sum + value, 0);
    const pairTotal = values[index] + values[index + 1];
    const nextLeft = Math.max(minSize, Math.min(pairTotal - minSize, positionPercent - before));
    const next = [...values];
    next[index] = nextLeft;
    next[index + 1] = pairTotal - nextLeft;
    return next;
  };

  const startSplitResize = (type, index, event) => {
    event.preventDefault();
    const container = splitContainerRef.current;
    if (!container) {
      return;
    }

    const rect = container.getBoundingClientRect();

    const handleMouseMove = (moveEvent) => {
      if (type === 'column') {
        const positionPercent = ((moveEvent.clientX - rect.left) / rect.width) * 100;
        setSplitColumns((prev) => resizeAdjacent(prev, index, positionPercent));
        return;
      }

      const positionPercent = ((moveEvent.clientY - rect.top) / rect.height) * 100;
      setSplitRows((prev) => resizeAdjacent(prev, index, positionPercent));
    };

    const handleMouseUp = () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
  };

  const renderResizeHandles = () => {
    if (!splitSettings.enabled) {
      return null;
    }

    const columnHandles = splitColumns.slice(0, -1).map((_, index) => {
      const left = splitColumns.slice(0, index + 1).reduce((sum, value) => sum + value, 0);
      return (
        <div
          key={`column-${index}`}
          role="separator"
          aria-orientation="vertical"
          onMouseDown={(event) => startSplitResize('column', index, event)}
          className="absolute inset-y-0 z-20 w-2 -translate-x-1/2 cursor-col-resize bg-transparent transition hover:bg-sky-300/40"
          style={{ left: `${left}%` }}
        />
      );
    });

    const rowHandles = splitSettings.mode === '4'
      ? splitRows.slice(0, -1).map((_, index) => {
          const top = splitRows.slice(0, index + 1).reduce((sum, value) => sum + value, 0);
          return (
            <div
              key={`row-${index}`}
              role="separator"
              aria-orientation="horizontal"
              onMouseDown={(event) => startSplitResize('row', index, event)}
              className="absolute inset-x-0 z-20 h-2 -translate-y-1/2 cursor-row-resize bg-transparent transition hover:bg-sky-300/40"
              style={{ top: `${top}%` }}
            />
          );
        })
      : null;

    return (
      <>
        {columnHandles}
        {rowHandles}
      </>
    );
  };

  const splitCount = Number(splitSettings.mode) || 2;
  const splitGridStyle = {
    gridTemplateColumns: splitColumns.map((value) => `${value}fr`).join(' '),
    gridTemplateRows: splitSettings.mode === '4'
      ? splitRows.map((value) => `${value}fr`).join(' ')
      : '1fr',
  };

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      {!splitSettings.enabled && (
        <Sidebar activeMenu={activeMenu} setActiveMenu={handleMenuChange} />
      )}

      <main className={`screen-capture-source w-full ${splitSettings.enabled ? 'h-screen p-3' : 'mx-auto max-w-[1720px] px-4 py-6 sm:px-6 lg:px-8'}`}>
        {splitSettings.enabled ? (
          <div ref={splitContainerRef} className="relative grid h-full gap-0" style={splitGridStyle}>
            {Array.from({ length: splitCount }, (_, index) => (
              <section
                key={index}
                onDragOver={handleSplitDragOver}
                onDragLeave={handleSplitDragLeave}
                onDrop={(event) => handleDropSplitScreen(event, index)}
                className="min-h-0 min-w-0 overflow-auto rounded-lg border border-slate-200 bg-slate-50 shadow-sm"
              >
                {splitScreens[index] ? (
                  <div className="relative flex h-full min-h-0 flex-col bg-white">
                    {draggingSavedScreen && (
                      <div
                        onDragOver={handleSplitDragOver}
                        onDrop={(event) => handleDropSplitScreen(event, index)}
                        className="absolute inset-0 z-30 flex items-center justify-center border-2 border-dashed border-sky-400 bg-sky-50/80 text-sm font-bold text-sky-700"
                      >
                        이 영역으로 화면 교체
                      </div>
                    )}
                    {!isPopupScreen(splitScreens[index]) && (
                      <div className="flex h-10 shrink-0 items-center justify-between border-b border-slate-200 bg-slate-900 px-3 text-white">
                        <div className="min-w-0 truncate text-sm font-semibold">
                          {splitScreens[index].preview?.heading || splitScreens[index].title || `${index + 1}번 분할 화면`}
                        </div>
                        <button
                          type="button"
                          onClick={() => handleCloseSplitScreen(index)}
                          className="ml-3 rounded-md border border-slate-600 px-2 py-1 text-xs font-semibold text-slate-100 transition hover:bg-slate-800"
                        >
                          창닫기
                        </button>
                      </div>
                    )}
                    <iframe
                      title={splitScreens[index].title || `${index + 1}번 분할 화면`}
                      src={buildEmbeddedUrl(splitScreens[index].url, index)}
                      className="min-h-0 flex-1 border-0 bg-white"
                    />
                  </div>
                ) : index === 0 ? (
                  <div className="min-h-full">
                    <Sidebar activeMenu={activeMenu} setActiveMenu={handleMenuChange} />
                    <div className="p-3">
                      {renderActivePage()}
                    </div>
                  </div>
                ) : (
                  <div className="m-3 flex h-[calc(100%-1.5rem)] min-h-[220px] items-center justify-center rounded-md border border-dashed border-slate-300 bg-white text-sm font-semibold text-slate-400">
                    화면을 Drag 해주세요.
                  </div>
                )}
              </section>
            ))}
            {renderResizeHandles()}
          </div>
        ) : (
          renderActivePage()
        )}
      </main>

      <SplitScreenEdgePanel />
    </div>
  );
}
