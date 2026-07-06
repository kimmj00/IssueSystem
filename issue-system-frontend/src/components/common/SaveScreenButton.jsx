import React from 'react';
import { animateSavedScreen, saveCurrentScreen } from '../../utils/savedScreens';

const MAX_PREVIEW_HTML_LENGTH = 60000;
const UNSAFE_SCRIPT_PROTOCOL = `java${'script'}:`;

function buildSearchDescription(url) {
  try {
    const params = new URL(url, window.location.href).searchParams;
    const keyword = params.get('keyword')?.trim();
    return keyword ? `검색어 : ${keyword}` : '';
  } catch (e) {
    return '';
  }
}

function sanitizePreviewClone(clone) {
  clone.querySelectorAll('script, style, link, iframe, object, embed').forEach((element) => {
    element.remove();
  });

  clone.querySelectorAll('*').forEach((element) => {
    Array.from(element.attributes).forEach((attribute) => {
      const name = attribute.name.toLowerCase();
      const value = attribute.value.trim().toLowerCase();
      if (name.startsWith('on') || value.startsWith(UNSAFE_SCRIPT_PROTOCOL)) {
        element.removeAttribute(attribute.name);
      }
    });
  });
}

export default function SaveScreenButton({
  title,
  url,
  openMode = 'page',
  fixed = true,
  buttonClassName = '',
}) {
  const buildPreview = () => {
    const source = document.querySelector('.screen-capture-source') || document.querySelector('main') || document.body;
    const sourceRect = source.getBoundingClientRect();
    const heading = source.querySelector('h1')?.textContent?.trim() || title;
    const detailDescription = Array.from(source.querySelectorAll('h2'))
      .map((element) => element.textContent?.trim())
      .find((text) => text && text !== heading) || '';
    const searchDescription = openMode === 'popup' ? '' : buildSearchDescription(url);
    const description = searchDescription || detailDescription;
    const labels = Array.from(source.querySelectorAll('label, th, h2'))
      .map((element) => element.textContent?.trim())
      .filter(Boolean)
      .slice(0, 6);
    const clone = source.cloneNode(true);
    clone.querySelectorAll('.fixed, .sticky, .screen-save-ui').forEach((element) => {
      element.remove();
    });
    sanitizePreviewClone(clone);

    let html = clone.innerHTML;
    if (html.length > MAX_PREVIEW_HTML_LENGTH) {
      html = html.slice(0, MAX_PREVIEW_HTML_LENGTH);
    }

    return {
      heading,
      description,
      labels,
      html,
      className: source.className,
      width: Math.max(Math.round(sourceRect.width), 1),
      height: Math.max(Math.round(sourceRect.height), 1),
    };
  };

  const handleSave = () => {
    const preview = buildPreview();
    saveCurrentScreen(
      { title, url, openMode, preview },
      {
        notifyOpener: openMode === 'popup',
        localSave: openMode !== 'popup',
      }
    );

    animateSavedScreen(preview, openMode === 'popup' ? { target: 'bottom-right' } : undefined);
  };

  return (
    <button
      type="button"
      onClick={handleSave}
      title="화면 임시저장"
      aria-label="현재 화면 임시저장"
      className={`screen-save-ui ${fixed ? 'fixed left-4 z-40' : ''} flex h-9 w-9 items-center justify-center rounded-md border border-slate-300 bg-white text-slate-700 shadow-md transition hover:border-sky-300 hover:bg-sky-50 hover:text-sky-700 focus:outline-none focus:ring-2 focus:ring-sky-400 ${buttonClassName || (fixed ? 'top-[84px]' : '')}`}
    >
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="h-5 w-5"
        aria-hidden="true"
      >
        <rect x="3" y="4" width="18" height="16" rx="2" />
        <path d="M3 8h18" />
        <path d="M8 4v4" />
      </svg>
    </button>
  );
}
