const DATE_HEADER_PATTERN = /^(\d{1,2}[/.]\d{1,2}(?:(?:\s*[~-]\s*\d{1,2}(?:[/.]\d{1,2})?)|(?:\s*,\s*(?:\d{1,2}[/.])?\d{1,2}))*)\s*(?:\(([^)]+)\))?\s*(.*)$/;

function twoDigits(value) {
  return String(Number(value)).padStart(2, '0');
}

function normalizeDateList(value) {
  const parts = String(value).trim().split(/\s*,\s*/);
  if (parts.length === 1) return parts[0];

  const firstDate = parts[0].match(/^(\d{1,2})[/.](\d{1,2})$/);
  if (!firstDate) return parts.join(', ');

  const month = twoDigits(firstDate[1]);
  return parts.map((part, index) => {
    const fullDate = part.match(/^(\d{1,2})[/.](\d{1,2})$/);
    if (fullDate) return `${twoDigits(fullDate[1])}/${twoDigits(fullDate[2])}`;

    if (index > 0 && /^\d{1,2}$/.test(part)) return `${month}/${twoDigits(part)}`;
    return part;
  }).join(', ');
}

export function parseTimelineDateHeader(line) {
  const matched = String(line).match(DATE_HEADER_PATTERN);
  if (!matched) return null;

  return {
    label: normalizeDateList(matched[1]),
    type: matched[2] || '',
    content: matched[3] || '',
  };
}
