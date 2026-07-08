import React, { useEffect, useState } from 'react';
import {
  SAVE_SCREEN_CHANNEL,
  SAVE_SCREEN_MESSAGE,
  SAVE_SCREEN_EVENT,
  SAVED_SCREEN_DRAG_END_EVENT,
  SAVED_SCREEN_DRAG_START_EVENT,
  animateSavedScreen,
  readSavedScreens,
  upsertSavedScreen,
  writeSavedScreens,
} from '../../utils/savedScreens';

function normalizeSavedScreenUrl(url) {
  const nextUrl = new URL(url, window.location.href);
  if (['localhost', '127.0.0.1'].includes(nextUrl.hostname)) {
    nextUrl.protocol = window.location.protocol;
    nextUrl.host = window.location.host;
  }
  return nextUrl.toString();
}

function screenHeading(screen) {
  if (screen.openMode === 'popup') {
    return screen.preview?.heading || `${screen.title || '팝업화면'} 상세보기`;
  }

  return screen.preview?.heading || screen.title || '저장 화면';
}

function screenDescription(screen) {
  if (screen.preview?.description) {
    return screen.preview.description;
  }

  const heading = screenHeading(screen);
  return (screen.preview?.labels || []).find((label) => label && label !== heading) || '';
}

export default function SplitScreenEdgePanel() {
  const [openedScreens, setOpenedScreens] = useState(readSavedScreens);
  const [previewScreen, setPreviewScreen] = useState(null);
  const [openTarget, setOpenTarget] = useState(() => localStorage.getItem('issue-system:saved-screen-open-target') || 'current');
  const [splitEnabled, setSplitEnabled] = useState(() => localStorage.getItem('issue-system:split-enabled') === 'true');
  const [splitMode, setSplitMode] = useState(() => localStorage.getItem('issue-system:split-mode') || '2');

  const handleCloseAll = () => {
    setOpenedScreens(writeSavedScreens([]));
  };

  const handleRemoveScreen = (event, screenId) => {
    event.stopPropagation();
    const nextScreens = openedScreens.filter((screen) => screen.id !== screenId);
    setOpenedScreens(writeSavedScreens(nextScreens));
  };

  const handleOpenScreen = (screen) => {
    const url = normalizeSavedScreenUrl(screen.url);
    if (screen.openMode === 'popup' || new URL(url, window.location.href).searchParams.get('popup')) {
      window.open(url, `saved-popup-${Date.now()}`, 'width=1200,height=820,left=120,top=80,scrollbars=yes,resizable=yes');
      return;
    }

    if (openTarget === 'new') {
      window.open(url, `saved-screen-${Date.now()}`, 'width=1400,height=900,left=80,top=60,scrollbars=yes,resizable=yes');
      return;
    }

    window.location.href = url;
  };

  const handleOpenTargetChange = (event) => {
    if (splitEnabled && event.target.value === 'new') {
      return;
    }

    const nextTarget = event.target.value;
    setOpenTarget(nextTarget);
    localStorage.setItem('issue-system:saved-screen-open-target', nextTarget);
  };

  const handleSplitEnabledChange = (event) => {
    const nextEnabled = event.target.checked;
    setSplitEnabled(nextEnabled);
    localStorage.setItem('issue-system:split-enabled', String(nextEnabled));
    window.dispatchEvent(new Event('issue-system:split-settings-change'));

    if (nextEnabled) {
      setOpenTarget('current');
      localStorage.setItem('issue-system:saved-screen-open-target', 'current');
    }
  };

  const handleSplitModeChange = (event) => {
    const nextSplitMode = event.target.value;
    setSplitMode(nextSplitMode);
    localStorage.setItem('issue-system:split-mode', nextSplitMode);
    window.dispatchEvent(new Event('issue-system:split-settings-change'));
  };

  const previewScale = (screen, maxWidth) => Math.min(0.62, maxWidth / (screen.preview?.width || 1200));

  useEffect(() => {
    const handleSaveScreen = (event) => {
      const nextScreen = event.detail;
      if (!nextScreen?.url) {
        return;
      }

      setOpenedScreens((prev) => {
        const filtered = prev.filter((screen) => screen.url !== nextScreen.url);
        const next = [nextScreen, ...filtered].slice(0, 20);
        return next;
      });
    };

    const handleStorage = (event) => {
      if (!event.key) {
        return;
      }

      setOpenedScreens(readSavedScreens());
    };

    const handleMessage = (event) => {
      if (event.origin !== window.location.origin || event.data?.type !== SAVE_SCREEN_MESSAGE) {
        return;
      }

      const nextScreen = event.data.screen;
      if (!nextScreen?.url) {
        return;
      }

      const nextScreens = upsertSavedScreen(nextScreen);
      setOpenedScreens(nextScreens);

      if (event.data.animate) {
        animateSavedScreen(nextScreen.preview);
      }
    };
    const handleChannelMessage = (event) => {
      if (event.data?.type !== SAVE_SCREEN_MESSAGE) {
        return;
      }

      const nextScreen = event.data.screen;
      if (!nextScreen?.url) {
        return;
      }

      const nextScreens = upsertSavedScreen(nextScreen);
      setOpenedScreens(nextScreens);
    };
    const channel = 'BroadcastChannel' in window
      ? new BroadcastChannel(SAVE_SCREEN_CHANNEL)
      : null;

    window.addEventListener(SAVE_SCREEN_EVENT, handleSaveScreen);
    window.addEventListener('storage', handleStorage);
    window.addEventListener('message', handleMessage);
    if (channel) {
      channel.addEventListener('message', handleChannelMessage);
    }
    return () => {
      window.removeEventListener(SAVE_SCREEN_EVENT, handleSaveScreen);
      window.removeEventListener('storage', handleStorage);
      window.removeEventListener('message', handleMessage);
      if (channel) {
        channel.removeEventListener('message', handleChannelMessage);
        channel.close();
      }
    };
  }, []);

  return (
    <aside className="screen-save-ui group fixed inset-y-0 right-0 z-50 flex w-72 translate-x-[calc(100%-16px)] transition-transform duration-500 ease-out hover:translate-x-0 focus-within:translate-x-0">
      <div className="h-24 w-4 rounded-l-md border-y border-l border-slate-300 bg-white/95 shadow-sm" />

      <section
        className="flex h-screen w-64 flex-col border-l border-slate-200 bg-white/95 p-4 shadow-2xl backdrop-blur"
        aria-label="분할 화면 패널"
      >
        <div className="flex min-h-0 flex-1 flex-col space-y-3">
          <button
            type="button"
            onClick={handleCloseAll}
            className="h-10 w-full rounded-md bg-slate-900 text-sm font-semibold text-white transition hover:bg-slate-700 focus:outline-none focus:ring-2 focus:ring-slate-500 disabled:cursor-not-allowed disabled:bg-slate-300"
            disabled={openedScreens.length === 0}
          >
            전체닫기
          </button>

          <div className="rounded-md border border-slate-200 bg-slate-50 p-2">
            <div className="mb-2 text-xs font-semibold text-slate-500">새 화면형태</div>
            <div className="grid grid-cols-2 gap-1 rounded-md bg-white p-1">
              <label className={`flex h-8 cursor-pointer items-center justify-center rounded text-xs font-semibold transition ${openTarget === 'new' ? 'bg-slate-900 text-white' : 'text-slate-500 hover:bg-slate-50'}`}>
                <input
                  type="radio"
                  name="saved-screen-open-target"
                  value="new"
                  checked={openTarget === 'new'}
                  onChange={handleOpenTargetChange}
                  disabled={splitEnabled}
                  className="sr-only"
                />
                새창
              </label>
              <label className={`flex h-8 cursor-pointer items-center justify-center rounded text-xs font-semibold transition ${openTarget === 'current' ? 'bg-slate-900 text-white' : 'text-slate-500 hover:bg-slate-50'}`}>
                <input
                  type="radio"
                  name="saved-screen-open-target"
                  value="current"
                  checked={openTarget === 'current'}
                  onChange={handleOpenTargetChange}
                  className="sr-only"
                />
                기존창
              </label>
            </div>
          </div>

          <div className="rounded-md border border-slate-200 bg-slate-50 p-2">
            <div className="mb-2 flex items-center justify-between gap-2">
              <div className="text-xs font-semibold text-slate-500">화면 분할 방식</div>
              <label className="inline-flex cursor-pointer items-center gap-1 rounded-full bg-white p-1 text-[11px] font-bold text-slate-500">
                <span className={splitEnabled ? 'text-slate-400' : 'text-slate-900'}>OFF</span>
                <input
                  type="checkbox"
                  checked={splitEnabled}
                  onChange={handleSplitEnabledChange}
                  className="sr-only"
                />
                <span className={`relative h-5 w-9 rounded-full transition ${splitEnabled ? 'bg-slate-900' : 'bg-slate-300'}`}>
                  <span className={`absolute top-0.5 h-4 w-4 rounded-full bg-white shadow transition ${splitEnabled ? 'left-4' : 'left-0.5'}`} />
                </span>
                <span className={splitEnabled ? 'text-slate-900' : 'text-slate-400'}>ON</span>
              </label>
            </div>
            <div className="grid grid-cols-3 gap-1 rounded-md bg-white p-1">
              {['2', '3', '4'].map((mode) => (
                <label
                  key={mode}
                  className={`flex h-8 cursor-pointer items-center justify-center rounded text-xs font-semibold transition ${splitMode === mode ? 'bg-slate-900 text-white' : 'text-slate-500 hover:bg-slate-50'}`}
                >
                  <input
                    type="radio"
                    name="saved-screen-split-mode"
                    value={mode}
                    checked={splitMode === mode}
                    onChange={handleSplitModeChange}
                    className="sr-only"
                  />
                  {mode}분할
                </label>
              ))}
            </div>
          </div>

          <div className="h-1.5 w-10 rounded-full bg-slate-300" />
          {openedScreens.length > 0 ? (
            <div className="grid min-h-0 flex-1 content-start gap-2 overflow-y-auto pr-1">
              {openedScreens.map((screen) => (
                <button
                  key={screen.id}
                  type="button"
                  draggable
                  onDragStart={(event) => {
                    event.dataTransfer.setData('application/json', JSON.stringify(screen));
                    event.dataTransfer.effectAllowed = 'copy';
                    window.dispatchEvent(new Event(SAVED_SCREEN_DRAG_START_EVENT));
                  }}
                  onDragEnd={() => window.dispatchEvent(new Event(SAVED_SCREEN_DRAG_END_EVENT))}
                  onClick={() => handleOpenScreen(screen)}
                  className="relative min-h-20 rounded-md border border-slate-200 bg-white px-3 py-2 text-left shadow-sm transition hover:border-sky-300 hover:bg-sky-50 focus:outline-none focus:ring-2 focus:ring-sky-400"
                  aria-label={`${screen.title} 화면으로 이동`}
                >
                  <span
                    role="button"
                    tabIndex={0}
                    onClick={(event) => handleRemoveScreen(event, screen.id)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        handleRemoveScreen(event, screen.id);
                      }
                    }}
                    className="absolute left-1.5 top-1.5 z-10 flex h-6 w-6 items-center justify-center rounded-full border border-slate-200 bg-white/95 text-xs font-black text-slate-500 shadow-sm transition hover:border-red-200 hover:bg-red-50 hover:text-red-600"
                    aria-label={`${screen.title} 임시저장 삭제`}
                  >
                    X
                  </span>
                  <span className="mb-2 block min-w-0 pl-7 pr-1">
                    <span className="block truncate text-sm font-bold leading-5 text-slate-950">
                      {screenHeading(screen)}
                    </span>
                    {screenDescription(screen) ? (
                      <span className="mt-0.5 block truncate text-xs font-semibold leading-4 text-slate-700">
                        {screenDescription(screen)}
                      </span>
                    ) : null}
                    <span className="mt-0.5 block truncate text-[11px] font-medium leading-4 text-slate-500">
                      {screen.savedAt ? new Date(screen.savedAt).toLocaleString() : ''}
                    </span>
                  </span>
                  <span
                    className="block overflow-hidden rounded-md border border-slate-200 bg-white shadow-sm"
                    onMouseEnter={() => setPreviewScreen(screen)}
                    onMouseLeave={() => setPreviewScreen(null)}
                    onFocus={() => setPreviewScreen(screen)}
                    onBlur={() => setPreviewScreen(null)}
                  >
                    <span className="flex h-3 items-center gap-1 bg-slate-900 px-1.5">
                      <span className="h-1.5 w-1.5 rounded-full bg-red-300" />
                      <span className="h-1.5 w-1.5 rounded-full bg-yellow-300" />
                      <span className="h-1.5 w-1.5 rounded-full bg-green-300" />
                    </span>
                    {screen.preview?.html ? (
                      <span className="saved-screen-preview block">
                        <span
                          className={`saved-screen-preview-content ${screen.preview.className || ''}`}
                          style={{
                            width: `${screen.preview.width || 1200}px`,
                            height: `${screen.preview.height || 800}px`,
                            transform: `scale(${previewScale(screen, 210)})`,
                          }}
                          dangerouslySetInnerHTML={{ __html: screen.preview.html }}
                        />
                      </span>
                    ) : (
                      <span className="block space-y-1.5 bg-slate-50 p-2">
                        <span className="block h-2 w-3/4 rounded bg-sky-200" />
                        {(screen.preview?.labels || []).slice(0, 3).map((label, index) => (
                          <span
                            key={`${screen.id}-${label}-${index}`}
                            className="block h-1.5 rounded bg-slate-200"
                            style={{ width: `${86 - index * 16}%` }}
                          />
                        ))}
                      </span>
                    )}
                  </span>
                </button>
              ))}
            </div>
          ) : (
            <div className="flex h-32 items-center justify-center rounded-md border border-dashed border-slate-200 bg-slate-50 text-sm font-medium text-slate-400">
              열린 화면 없음
            </div>
          )}
        </div>
      </section>

      {previewScreen?.preview?.html ? (
        <div
          className="saved-screen-hover-preview screen-save-ui fixed right-72 top-24 z-[70] overflow-hidden rounded-xl border border-sky-200 bg-white shadow-2xl"
          aria-hidden="true"
        >
          <div className="flex h-7 items-center gap-1.5 bg-slate-900 px-3">
            <span className="h-2 w-2 rounded-full bg-red-300" />
            <span className="h-2 w-2 rounded-full bg-yellow-300" />
            <span className="h-2 w-2 rounded-full bg-green-300" />
          </div>
          <div className="saved-screen-hover-viewport">
            <div
              className={`saved-screen-hover-content ${previewScreen.preview.className || ''}`}
              style={{
                width: `${previewScreen.preview.width || 1200}px`,
                height: `${previewScreen.preview.height || 800}px`,
                transform: `scale(${previewScale(previewScreen, 520)})`,
              }}
              dangerouslySetInnerHTML={{ __html: previewScreen.preview.html }}
            />
          </div>
        </div>
      ) : null}
    </aside>
  );
}
