function text(value) {
  return value == null ? '' : String(value).trim();
}

function compact(value) {
  return text(value).replace(/\s+/g, '').toLowerCase();
}

export function getKeywordTerms(keyword) {
  return text(keyword)
    .split(/\s+/)
    .map((word) => word.trim())
    .filter(Boolean);
}

export function getKeywordCharacters(keyword) {
  const normalized = compact(keyword);
  return [...new Set(Array.from(normalized).filter(Boolean))];
}

export function getFallbackMinimumCharacterMatches(keyword) {
  const length = getKeywordCharacters(keyword).length;

  if (length >= 5) {
    return 3;
  }

  if (length >= 3) {
    return 2;
  }

  return Number.POSITIVE_INFINITY;
}

export function includesKeyword(value, term) {
  const target = text(value).toLowerCase();
  const keyword = text(term).toLowerCase();

  return Boolean(target && keyword && target.includes(keyword));
}

function commonPrefixLength(left, right) {
  const limit = Math.min(left.length, right.length);
  let count = 0;

  while (count < limit && left[count] === right[count]) {
    count += 1;
  }

  return count;
}

function damerauLevenshteinDistance(left, right) {
  const distance = Array.from({ length: left.length + 1 }, () =>
    Array(right.length + 1).fill(0)
  );

  for (let i = 0; i <= left.length; i += 1) {
    distance[i][0] = i;
  }

  for (let j = 0; j <= right.length; j += 1) {
    distance[0][j] = j;
  }

  for (let i = 1; i <= left.length; i += 1) {
    for (let j = 1; j <= right.length; j += 1) {
      const cost = left[i - 1] === right[j - 1] ? 0 : 1;
      let next = Math.min(
        distance[i - 1][j] + 1,
        distance[i][j - 1] + 1,
        distance[i - 1][j - 1] + cost
      );

      if (
        i > 1
        && j > 1
        && left[i - 1] === right[j - 2]
        && left[i - 2] === right[j - 1]
      ) {
        next = Math.min(next, distance[i - 2][j - 2] + 1);
      }

      distance[i][j] = next;
    }
  }

  return distance[left.length][right.length];
}

function typoSimilarityFragment(value, keyword) {
  const source = text(value).toLowerCase();
  const query = text(keyword).toLowerCase();

  if (!source || !query) {
    return 0;
  }

  const maxLength = Math.max(source.length, query.length);
  const distance = damerauLevenshteinDistance(source, query);
  let similarity = 1 - (distance / maxLength);

  if (commonPrefixLength(source, query) >= 3) {
    similarity += 0.08;
  }

  return Math.min(similarity, 1);
}

function comparableFragments(value) {
  const target = text(value).toLowerCase();
  const fragments = new Set([target, compact(target)]);

  getKeywordTerms(target).forEach((term) => fragments.add(term.toLowerCase()));
  target.match(/[a-z0-9]+/g)?.forEach((fragment) => fragments.add(fragment));

  return [...fragments].filter(Boolean);
}

export function calculateTypoSimilarity(value, keyword) {
  const query = text(keyword).toLowerCase();
  const compactQuery = compact(keyword);

  if (!query) {
    return 0;
  }

  return comparableFragments(value).reduce((best, fragment) => Math.max(
    best,
    typoSimilarityFragment(fragment, query),
    typoSimilarityFragment(compact(fragment), compactQuery)
  ), 0);
}

export function isTypoCandidate(value, keyword) {
  const threshold = compact(keyword).length <= 3 ? 0.66 : 0.74;

  return calculateTypoSimilarity(value, keyword) >= threshold;
}

export function getCharacterMatchCount(value, keyword) {
  const target = text(value).toLowerCase();
  return getKeywordCharacters(keyword)
    .filter((character) => target.includes(character))
    .length;
}

export function hasFallbackCharacterMatch(value, keyword) {
  return getCharacterMatchCount(value, keyword) >= getFallbackMinimumCharacterMatches(keyword);
}

export function matchesFuzzyKeyword(value, keyword) {
  const target = text(value).toLowerCase();
  const query = text(keyword).toLowerCase();

  if (!query) {
    return true;
  }

  if (!target) {
    return false;
  }

  if (target.includes(query)) {
    return true;
  }

  const compactTarget = compact(target);
  const compactQuery = compact(query);

  if (compactQuery && compactTarget.includes(compactQuery)) {
    return true;
  }

  const terms = getKeywordTerms(query);
  if (terms.some((term) => target.includes(term.toLowerCase()))) {
    return true;
  }

  if (isTypoCandidate(target, query)) {
    return true;
  }

  return hasFallbackCharacterMatch(target, query);
}

export function calculateFuzzyMatchScore(values, keyword) {
  const normalizedValues = values
    .map((value) => text(value).toLowerCase().replace(/\s+/g, ' ').trim())
    .filter(Boolean);
  const terms = getKeywordTerms(keyword).map((term) => term.toLowerCase());
  const query = terms.join(' ');
  const compactQuery = compact(query);

  if (normalizedValues.length === 0 || !query) {
    return 0;
  }

  const exactQuery = normalizedValues.some((value) => value.includes(query));
  if (exactQuery) {
    return 1_000_000;
  }

  const compactQueryMatch = compactQuery
    && normalizedValues.some((value) => compact(value).includes(compactQuery));
  if (compactQueryMatch) {
    return 900_000;
  }

  const typoSimilarity = normalizedValues.reduce(
    (best, value) => Math.max(best, calculateTypoSimilarity(value, query)),
    0
  );
  const typoThreshold = compactQuery.length <= 3 ? 0.66 : 0.74;
  if (typoSimilarity >= typoThreshold) {
    return 800_000 + Math.floor(typoSimilarity * 1_000);
  }

  const matchedTermCount = terms.reduce(
    (count, term) => count + normalizedValues.filter((value) => value.includes(term)).length,
    0
  );
  if (matchedTermCount > 0) {
    return 700_000 + Math.min(matchedTermCount, 20);
  }

  const matchedCharacterCount = getKeywordCharacters(query).filter((character) =>
    normalizedValues.some((value) => value.includes(character))
  ).length;

  if (matchedCharacterCount >= getFallbackMinimumCharacterMatches(query)) {
    return 1_000 + Math.min(matchedCharacterCount, 20);
  }

  return 0;
}

function groupMatch(values, keyword) {
  const normalizedValues = values
    .map((value) => text(value).toLowerCase().replace(/\s+/g, ' ').trim())
    .filter(Boolean);
  const terms = getKeywordTerms(keyword).map((term) => term.toLowerCase());
  const query = terms.join(' ');
  const compactQuery = compact(query);

  if (normalizedValues.length === 0 || !query) {
    return { type: 'none', tie: 0 };
  }

  if (normalizedValues.some((value) => value.includes(query))) {
    return { type: 'exact', tie: 0 };
  }

  if (compactQuery && normalizedValues.some((value) => compact(value).includes(compactQuery))) {
    return { type: 'compact', tie: 0 };
  }

  const typoSimilarity = normalizedValues.reduce(
    (best, value) => Math.max(best, calculateTypoSimilarity(value, query)),
    0
  );
  const typoThreshold = compactQuery.length <= 3 ? 0.66 : 0.74;
  if (typoSimilarity >= typoThreshold) {
    return { type: 'typo', tie: Math.floor(typoSimilarity * 1_000) };
  }

  const matchedTermCount = terms.reduce(
    (count, term) => count + normalizedValues.filter((value) => value.includes(term)).length,
    0
  );
  if (matchedTermCount > 0) {
    return { type: 'token', tie: Math.min(matchedTermCount, 20) };
  }

  const matchedCharacterCount = getKeywordCharacters(query).filter((character) =>
    normalizedValues.some((value) => value.includes(character))
  ).length;

  if (matchedCharacterCount >= getFallbackMinimumCharacterMatches(query)) {
    return { type: 'fallback', tie: Math.min(matchedCharacterCount, 20) };
  }

  return { type: 'none', tie: 0 };
}

export function calculateStructuredFuzzyMatchScore({
  titleValues = [],
  bodyValues = [],
  auxiliaryValues = [],
}, keyword) {
  const title = groupMatch(titleValues, keyword);
  if (title.type === 'exact') return 1_000_000;
  if (title.type === 'compact') return 900_000;
  if (title.type === 'typo') return 800_000 + title.tie;
  if (title.type === 'token') return 700_000 + title.tie;

  const body = groupMatch(bodyValues, keyword);
  if (body.type === 'exact') return 500_000;
  if (body.type === 'compact') return 450_000;
  if (body.type === 'typo') return 400_000 + body.tie;
  if (body.type === 'token') return 300_000 + body.tie;

  const auxiliary = groupMatch(auxiliaryValues, keyword);
  if (auxiliary.type === 'token') return 200_000 + auxiliary.tie;

  const fallbackTie = Math.max(
    title.type === 'fallback' ? title.tie : 0,
    body.type === 'fallback' ? body.tie : 0,
    auxiliary.type === 'fallback' ? auxiliary.tie : 0
  );

  return fallbackTie > 0 ? 1_000 + fallbackTie : 0;
}

export function sortByFuzzyMatch(items, keyword, getValue = (item) => item) {
  const query = text(keyword);

  if (!query) {
    return items;
  }

  return items
    .map((item, index) => ({
      item,
      index,
      score: calculateFuzzyMatchScore([getValue(item)], query),
    }))
    .filter(({ score }) => score > 0)
    .sort((a, b) => b.score - a.score || a.index - b.index)
    .map(({ item }) => item);
}
