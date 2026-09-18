import { getHighlightTerms, highlightHtml } from './highlightText';

const koreanService = '\uC11C\uBE44\uC2A4';
const koreanTypo = '\uC11C\uC9C0\uC2A4';

describe('highlightText', () => {
  test('prefers full keyword highlights when the keyword directly matches', () => {
    expect(getHighlightTerms('agent install', 'agent')).toEqual(['agent']);
    expect(getHighlightTerms(`${koreanService} install`, koreanService)).toEqual([koreanService]);
  });

  test('falls back to characters only when enough characters match', () => {
    expect(getHighlightTerms('agnet install', 'agent')).toEqual(['a', 'g', 'e', 'n', 't']);
    expect(getHighlightTerms(`${koreanService} install`, koreanTypo)).toEqual(['\uC11C', '\uC2A4']);
    expect(getHighlightTerms('alpha', 'agent')).toEqual([]);
  });

  test('highlights sanitized html text without removing tags', () => {
    const html = highlightHtml('<p>agent <strong>install</strong></p>', 'agent');

    expect(html).toContain('<p><mark');
    expect(html).toContain('agent</mark> <strong>install</strong></p>');
  });
});
