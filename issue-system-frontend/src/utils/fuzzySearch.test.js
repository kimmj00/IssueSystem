import {
  calculateFuzzyMatchScore,
  calculateStructuredFuzzyMatchScore,
  calculateTypoSimilarity,
  getFallbackMinimumCharacterMatches,
  getKeywordCharacters,
  hasFallbackCharacterMatch,
  matchesFuzzyKeyword,
  sortByFuzzyMatch,
} from './fuzzySearch';

const koreanService = '\uC11C\uBE44\uC2A4';
const koreanTypo = '\uC11C\uC9C0\uC2A4';

describe('fuzzySearch', () => {
  test('splits English and Korean search words by character', () => {
    expect(getKeywordCharacters('agent')).toEqual(['a', 'g', 'e', 'n', 't']);
    expect(getKeywordCharacters(koreanService)).toEqual(['\uC11C', '\uBE44', '\uC2A4']);
  });

  test('matches typo candidates by edit similarity', () => {
    expect(calculateTypoSimilarity('agent install', 'agnet')).toBeGreaterThanOrEqual(0.74);
    expect(calculateTypoSimilarity('k3s data Agent가 죽는 문제', 'agnet')).toBeGreaterThanOrEqual(0.74);
    expect(calculateTypoSimilarity('AGENT_BINDIP option', 'agnet')).toBeGreaterThanOrEqual(0.74);
    expect(matchesFuzzyKeyword('agent install', 'agnet')).toBe(true);
    expect(matchesFuzzyKeyword(`${koreanService} install`, koreanTypo)).toBe(true);
  });

  test('keeps fallback character matches weak and thresholded', () => {
    expect(getFallbackMinimumCharacterMatches('agent')).toBe(3);
    expect(hasFallbackCharacterMatch('alpha', 'agent')).toBe(false);
    expect(matchesFuzzyKeyword('alpha', 'agent')).toBe(false);
  });

  test('keeps exact matches above typo and character-only matches', () => {
    const sorted = sortByFuzzyMatch(['urgent notice', 'agnet', 'agent'], 'agent');

    expect(sorted).toEqual(['agent', 'agnet', 'urgent notice']);
    expect(calculateFuzzyMatchScore(['agent'], 'agent'))
      .toBeGreaterThan(calculateFuzzyMatchScore(['agnet'], 'agent'));
    expect(calculateFuzzyMatchScore(['agnet'], 'agent'))
      .toBeGreaterThan(calculateFuzzyMatchScore(['urgent notice'], 'agent'));
  });

  test('keeps title matches above body matches in structured scoring', () => {
    const titleTypo = calculateStructuredFuzzyMatchScore({
      titleValues: ['agnet'],
      bodyValues: [],
    }, 'agent');
    const bodyExact = calculateStructuredFuzzyMatchScore({
      titleValues: [],
      bodyValues: ['agent install guide'],
    }, 'agent');

    expect(titleTypo).toBeGreaterThan(bodyExact);
  });

  test('treats English fragments with Korean suffixes as title typo matches', () => {
    const titleWithSuffix = calculateStructuredFuzzyMatchScore({
      titleValues: ['k3s data Agent가 죽는 문제'],
      bodyValues: [],
    }, 'agnet');
    const bodyExact = calculateStructuredFuzzyMatchScore({
      titleValues: [],
      bodyValues: ['agnet install guide'],
    }, 'agnet');

    expect(titleWithSuffix).toBeGreaterThan(bodyExact);
  });
});
