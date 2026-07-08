export const SAVED_SCREENS_STORAGE_KEY = 'issue-system:saved-screens';
export const SAVE_SCREEN_EVENT = 'issue-system:save-screen';
export const SAVE_SCREEN_MESSAGE = 'issue-system:save-screen-message';
export const SAVE_SCREEN_CHANNEL = 'issue-system:saved-screens-channel';
export const SAVED_SCREEN_DRAG_START_EVENT = 'issue-system:saved-screen-drag-start';
export const SAVED_SCREEN_DRAG_END_EVENT = 'issue-system:saved-screen-drag-end';

const MAX_SAVED_SCREENS = 12;
const MIN_SAVED_SCREENS = 3;

function withoutPreviewHtml(screen) {
  if (!screen?.preview?.html) {
    return screen;
  }

  return {
    ...screen,
    preview: {
      ...screen.preview,
      html: '',
    },
  };
}

export function readSavedScreens() {
  try {
    const value = localStorage.getItem(SAVED_SCREENS_STORAGE_KEY);
    const parsed = value ? JSON.parse(value) : [];

    return Array.isArray(parsed) ? parsed : [];
  } catch (e) {
    return [];
  }
}

export function writeSavedScreens(screens) {
  const candidates = [
    screens.slice(0, MAX_SAVED_SCREENS),
    screens.slice(0, Math.max(Math.ceil(MAX_SAVED_SCREENS / 2), MIN_SAVED_SCREENS)),
    screens.slice(0, MIN_SAVED_SCREENS).map(withoutPreviewHtml),
  ];

  for (const candidate of candidates) {
    try {
      localStorage.setItem(SAVED_SCREENS_STORAGE_KEY, JSON.stringify(candidate));
      return candidate;
    } catch (e) {
      // 저장 용량 초과 시 더 작은 후보로 재시도합니다.
    }
  }

  localStorage.removeItem(SAVED_SCREENS_STORAGE_KEY);
  return [];
}

export function upsertSavedScreen(screen) {
  if (!screen?.url) {
    return readSavedScreens();
  }

  const savedScreens = readSavedScreens();
  const nextScreens = [
    screen,
    ...savedScreens.filter((savedScreen) => savedScreen.url !== screen.url),
  ].slice(0, MAX_SAVED_SCREENS);

  return writeSavedScreens(nextScreens);
}

export function saveCurrentScreen(screen, { notifyOpener = false, localSave = true } = {}) {
  const nextScreen = {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    savedAt: new Date().toISOString(),
    ...screen,
  };

  if (localSave) {
    upsertSavedScreen(nextScreen);
    window.dispatchEvent(
      new CustomEvent(SAVE_SCREEN_EVENT, {
        detail: nextScreen,
      })
    );
  }

  if (notifyOpener && window.opener && !window.opener.closed) {
    window.opener.postMessage(
      {
        type: SAVE_SCREEN_MESSAGE,
        screen: nextScreen,
        animate: false,
      },
      window.location.origin
    );
  }

  if (notifyOpener && 'BroadcastChannel' in window) {
    const channel = new BroadcastChannel(SAVE_SCREEN_CHANNEL);
    channel.postMessage({
      type: SAVE_SCREEN_MESSAGE,
      screen: nextScreen,
      animate: false,
    });
    channel.close();
  }

  return nextScreen;
}

export function animateSavedScreen(preview, options = {}) {
  const startWidth = Math.min(window.innerWidth * 0.78, 1080);
  const startHeight = Math.min(window.innerHeight * 0.68, 680);
  const startLeft = Math.max(24, window.innerWidth / 2 - startWidth / 2);
  const startTop = Math.max(92, window.innerHeight / 2 - startHeight / 2);
  const targetX = window.innerWidth - startLeft - 52;
  const targetY = options.target === 'bottom-right'
    ? window.innerHeight - startTop - 64
    : window.innerHeight / 2 - startTop - 18;
  const previewWidth = preview?.width || 1200;
  const scale = Math.max(startWidth / Math.max(previewWidth, 1), 0.1);

  const flyer = document.createElement('div');
  flyer.className = 'save-screen-capture-flyer';
  flyer.style.left = `${startLeft}px`;
  flyer.style.top = `${startTop}px`;
  flyer.style.width = `${startWidth}px`;
  flyer.style.height = `${startHeight}px`;
  flyer.style.setProperty('--save-screen-fly-x', `${targetX}px`);
  flyer.style.setProperty('--save-screen-fly-y', `${targetY}px`);

  const browserBar = document.createElement('div');
  browserBar.className = 'save-screen-capture-bar';
  browserBar.innerHTML = '<span></span><span></span><span></span>';

  const viewport = document.createElement('div');
  viewport.className = 'save-screen-capture-viewport';

  const content = document.createElement('div');
  content.className = `${preview?.className || ''} save-screen-capture-content`;
  content.style.width = `${previewWidth}px`;
  content.style.transform = `scale(${scale})`;
  content.style.transformOrigin = 'top left';
  content.innerHTML = preview?.html || '';

  viewport.appendChild(content);
  flyer.appendChild(browserBar);
  flyer.appendChild(viewport);
  document.body.appendChild(flyer);

  window.setTimeout(() => {
    flyer.remove();
  }, 1550);
}
