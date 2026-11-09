import { readFileSync } from 'node:fs';

export const { engines, dependencies, devDependencies } = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8'));

export function isSupportedNode(version = process.versions.node) {
  const current = version.split('.').map(Number);
  return engines.node.split('||').some((range) => {
    const match = range.trim().match(/^(\^|>=)(\d+)\.(\d+)\.(\d+)$/);
    if (!match) throw new Error(`확인할 수 없는 Node 버전 범위: ${range}`);
    const minimum = match.slice(2).map(Number);
    const comparison = current.map((value, index) => value - minimum[index]).find((value) => value !== 0) || 0;
    return comparison >= 0 && (match[1] !== '^' || current[0] === minimum[0]);
  });
}
