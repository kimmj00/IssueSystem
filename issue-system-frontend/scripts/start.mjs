import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { delimiter, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { isSupportedNode } from './node-version.mjs';

const frontendDir = fileURLToPath(new URL('../', import.meta.url));
const projectDir = dirname(frontendDir);

function run(command, args, options) {
  const result = spawnSync(command, args, { stdio: 'inherit', windowsHide: true, ...options });
  if (result.error) {
    console.error(`실행하지 못했습니다: ${command}`);
    console.error(result.error.message);
  }
  return result.status ?? (result.signal === 'SIGINT' ? 130 : 1);
}

if (!isSupportedNode()) {
  const version = readFileSync(new URL('../.node-version', import.meta.url), 'utf8').trim();
  if (!isSupportedNode(version)) {
    throw new Error(`.node-version에 지원하는 Node 버전을 지정하세요: ${version}`);
  }
  const platform = process.platform === 'win32' ? 'win' : process.platform;
  const executable = process.platform === 'win32' ? 'node.exe' : 'bin/node';
  const localNode = join(projectDir, '.gradle', 'nodejs', `node-v${version}-${platform}-${process.arch}`, executable);

  if (!existsSync(localNode)) {
    console.log(`프로젝트 전용 Node ${version}을 Gradle로 준비합니다.`);
    const windows = process.platform === 'win32';
    const status = run(windows ? process.env.ComSpec || 'cmd.exe' : 'sh', windows
      ? ['/d', '/s', '/c', 'gradlew.bat nodeSetup --console=plain']
      : ['./gradlew', 'nodeSetup', '--console=plain'], { cwd: projectDir });
    if (status !== 0) process.exit(status);
  }

  console.log(`Node.js ${process.version} 대신 프로젝트 전용 Node ${version}으로 실행합니다.`);
  const env = { ...process.env };
  const pathKey = Object.keys(env).find((key) => key.toLowerCase() === 'path') || 'PATH';
  env[pathKey] = `${dirname(localNode)}${delimiter}${env[pathKey] || ''}`;
  process.exit(run(localNode, [fileURLToPath(import.meta.url), ...process.argv.slice(2)], { cwd: frontendDir, env }));
}

// 네이티브 모듈을 현재 프로세스에 로드하기 전에 별도 프로세스로 검사한다.
// 검사 실패 후 npm ci를 실행해도 현재 프로세스가 .node 파일을 잠그지 않는다.
const verificationScript = fileURLToPath(new URL('./verify-build-tools.mjs', import.meta.url));
if (run(process.execPath, [verificationScript], { cwd: frontendDir, stdio: 'ignore' }) !== 0) {
  console.log('프론트엔드 의존성이 없거나 손상되어 package-lock.json 기준으로 복구합니다.');
  const bundledNpm = join(dirname(process.execPath), process.platform === 'win32'
    ? 'node_modules/npm/bin/npm-cli.js'
    : '../lib/node_modules/npm/bin/npm-cli.js');
  const npmCli = existsSync(bundledNpm) ? bundledNpm : process.env.npm_execpath;
  if (!npmCli) {
    console.error('npm을 찾을 수 없습니다. npm start로 실행하세요.');
    process.exit(1);
  }
  const env = { ...process.env };
  const pathKey = Object.keys(env).find((key) => key.toLowerCase() === 'path') || 'PATH';
  env[pathKey] = `${dirname(process.execPath)}${delimiter}${env[pathKey] || ''}`;
  const status = run(process.execPath, [npmCli, 'ci', '--include=dev', '--include=optional'], { cwd: frontendDir, env });
  if (status !== 0) process.exit(status);
}

await import('./verify-build-tools.mjs');
if (process.exitCode) process.exit(process.exitCode);
await import('../node_modules/vite/bin/vite.js');
