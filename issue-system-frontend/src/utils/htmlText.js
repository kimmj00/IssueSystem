export function decodeHtmlEntities(value) {
  let text = String(value || '');

  for (let index = 0; index < 2; index += 1) {
    const textarea = document.createElement('textarea');
    textarea.innerHTML = text;
    text = textarea.value;
  }

  return text;
}

export function stripHtml(value) {
  if (!value) {
    return '';
  }

  const textWithEntities = String(value)
      .replace(/<img\b[^>]*>/gi, ' ')
      .replace(/<[^>]*>/g, ' ');

  return decodeHtmlEntities(textWithEntities)
      .replace(/\u00a0/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
}
