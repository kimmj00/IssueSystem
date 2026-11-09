import { createRequire } from 'node:module';
import { engines, dependencies, devDependencies, isSupportedNode } from './node-version.mjs';

if (!isSupportedNode()) {
  console.error(`지원하지 않는 Node.js ${process.version}입니다. 필요 버전: ${engines.node}`);
  console.error(`실행 경로: ${process.execPath}`);
  console.error('Gradle은 프로젝트 전용 Node를 사용하지만, 직접 실행한 npm은 터미널/IntelliJ의 Node를 사용합니다.');
  console.error('개발 서버는 프론트 폴더에서 npm start로 실행하면 프로젝트 전용 Node를 자동 선택합니다.');
  console.error('IntelliJ에서는 "프론트엔드 개발 서버" 실행 구성을 선택하거나 npm 실행 구성의 Node 런타임을 지원 버전으로 설정하세요.');
  process.exit(1);
}

console.log(`Node.js ${process.version}: ${process.execPath}`);

try {
  // 설치 중단으로 Vite 외의 패키지만 누락된 경우도 시작 전에 찾는다.
  const require = createRequire(import.meta.url);
  for (const dependency of Object.keys({ ...dependencies, ...devDependencies })) {
    require.resolve(dependency);
  }
  // 설치 성공 여부와 별개로 플랫폼별 Rolldown 바인딩을 실제로 불러온다.
  await import('vite');
  console.log(`빌드 도구 로딩 정상: ${process.platform}/${process.arch}`);
} catch (error) {
  const missingDependency = error.code === 'ERR_MODULE_NOT_FOUND' || error.code === 'MODULE_NOT_FOUND';
  console.error(missingDependency ? '빌드 도구의 의존성 패키지를 찾을 수 없습니다.' : '빌드 도구의 네이티브 모듈을 불러오지 못했습니다.');
  console.error('개발 서버는 npm start로 실행하면 누락되거나 손상된 의존성을 자동 복구합니다.');
  console.error('프론트 폴더에서 npm ci --include=dev --include=optional 로 다시 설치하세요.');
  console.error('Gradle 빌드용 의존성은 프로젝트 루트에서 .\\gradlew.bat npmInstall (macOS/Linux: ./gradlew npmInstall)로 설치하세요.');
  console.error(error);
  process.exitCode = 1;
}
