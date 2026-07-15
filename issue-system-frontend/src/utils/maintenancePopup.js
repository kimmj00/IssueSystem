export function getMaintenancePopupFeatures() {
  const screenLeft = Number.isFinite(window.screen.availLeft) ? window.screen.availLeft : 0;
  const screenTop = Number.isFinite(window.screen.availTop) ? window.screen.availTop : 0;
  const screenWidth = window.screen.availWidth || window.innerWidth || 1200;
  const screenHeight = window.screen.availHeight || window.innerHeight || 900;
  const width = Math.max(320, Math.min(1200, screenWidth - 40));
  const height = Math.max(320, Math.min(820, screenHeight - 80));
  const left = screenLeft + Math.max(0, Math.floor((screenWidth - width) / 2));
  const top = screenTop + Math.max(0, Math.floor((screenHeight - height) / 2));

  return `width=${width},height=${height},left=${left},top=${top},scrollbars=yes,resizable=yes`;
}
