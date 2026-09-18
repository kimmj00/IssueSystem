import React from 'react';
import {
  getFallbackMinimumCharacterMatches,
  getKeywordCharacters,
  getKeywordTerms,
  includesKeyword,
} from './fuzzySearch';

const DEFAULT_MARK_CLASS =
  'rounded bg-yellow-100 px-0.5 text-yellow-900 ring-1 ring-yellow-200';

function text(value) {
  return value == null ? '' : String(value).trim();
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export function getHighlightTerms(value, keyword) {
  const source = text(value);
  const terms = getKeywordTerms(keyword);

  if (!source || terms.length === 0) {
    return [];
  }

  const directTerms = terms.filter((term) => includesKeyword(source, term));
  if (directTerms.length > 0) {
    return [...new Set(directTerms)]
      .filter(Boolean)
      .sort((left, right) => right.length - left.length);
  }

  const characterTerms = getKeywordCharacters(keyword)
    .filter((character) => includesKeyword(source, character));

  if (characterTerms.length < getFallbackMinimumCharacterMatches(keyword)) {
    return [];
  }

  return [...new Set(characterTerms)]
    .filter(Boolean)
    .sort((left, right) => right.length - left.length);
}

export function highlightText(value, keyword, markClassName = DEFAULT_MARK_CLASS) {
  const source = text(value);
  const highlightTerms = getHighlightTerms(source, keyword);

  if (!source || highlightTerms.length === 0) {
    return source || '-';
  }

  const pattern = new RegExp(
    `(${highlightTerms.map((term) => escapeRegExp(term)).join('|')})`,
    'gi'
  );

  return source.split(pattern).map((part, index) => {
    const matched = highlightTerms.some((term) => part.toLowerCase() === term.toLowerCase());

    if (!matched) {
      return part;
    }

    return (
      <mark key={`${part}-${index}`} className={markClassName}>
        {part}
      </mark>
    );
  });
}

export function highlightHtml(html, keyword, markClassName = DEFAULT_MARK_CLASS) {
  const source = html == null ? '' : String(html);

  if (!source) {
    return '';
  }

  if (typeof document === 'undefined') {
    return source;
  }

  const template = document.createElement('template');
  template.innerHTML = source;

  const visibleText = template.content.textContent || '';
  const highlightTerms = getHighlightTerms(visibleText, keyword);

  if (highlightTerms.length === 0) {
    return source;
  }

  const pattern = new RegExp(
    `(${highlightTerms.map((term) => escapeRegExp(term)).join('|')})`,
    'gi'
  );
  const textNodeFilter = window.NodeFilter ? window.NodeFilter.SHOW_TEXT : 4;
  const walker = document.createTreeWalker(template.content, textNodeFilter);
  const textNodes = [];

  while (walker.nextNode()) {
    textNodes.push(walker.currentNode);
  }

  textNodes.forEach((node) => {
    const value = node.nodeValue || '';

    if (!pattern.test(value)) {
      pattern.lastIndex = 0;
      return;
    }

    pattern.lastIndex = 0;
    const fragment = document.createDocumentFragment();
    value.split(pattern).forEach((part) => {
      if (!part) {
        return;
      }

      const matched = highlightTerms.some((term) => part.toLowerCase() === term.toLowerCase());
      if (!matched) {
        fragment.appendChild(document.createTextNode(part));
        return;
      }

      const mark = document.createElement('mark');
      mark.className = markClassName;
      mark.textContent = part;
      fragment.appendChild(mark);
    });

    node.replaceWith(fragment);
    pattern.lastIndex = 0;
  });

  return template.innerHTML;
}
