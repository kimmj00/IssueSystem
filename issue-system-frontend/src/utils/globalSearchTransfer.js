export const GLOBAL_SEARCH_TRANSFER_PARAM = 'fromGlobalSearch';

export const GLOBAL_SEARCH_TRANSFER_KEYS = [
  GLOBAL_SEARCH_TRANSFER_PARAM,
  'keyword',
  'customerName',
  'customer',
  'infraType',
  'infraTypes',
  'startDate',
  'endDate',
  'workIssueType',
  'tab',
  'innerTab',
  'salesRep',
  'executor',
];

export function isGlobalSearchTransfer() {
  return new URLSearchParams(window.location.search).get(GLOBAL_SEARCH_TRANSFER_PARAM) === '1';
}

export function isReloadNavigation() {
  const navigation = performance.getEntriesByType?.('navigation')?.[0];

  if (navigation?.type) {
    return navigation.type === 'reload';
  }

  return performance.navigation?.type === 1;
}

export function shouldIgnoreGlobalSearchTransfer() {
  return isGlobalSearchTransfer() && isReloadNavigation();
}

export function shouldApplyGlobalSearchTransfer() {
  return isGlobalSearchTransfer() && !isReloadNavigation();
}
