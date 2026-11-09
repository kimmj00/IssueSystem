# 프론트엔드 개발 및 배포

React 19.2.5를 유지하며 빌드·개발 서버를 Vite, 테스트 실행기를 Vitest로 교체했다.
`react-scripts`와 그 하위의 오래된 개발 서버·SVG 처리·테스트 의존성을 제거했다.

## 실행 환경

- 빌드 도구는 Node.js 22.13 이상인 22 LTS, 24 LTS 또는 26 이상을 사용한다.
- 개발 서버는 기존처럼 프론트 폴더에서 `npm start`로 실행한다. 터미널이 Node 20을 사용해도
  실행 스크립트가 프로젝트 전용 Node로 전환하므로 전역 Node나 IntelliJ 설정을 변경할 필요가 없다.
- Gradle 빌드는 시스템/IntelliJ의 Node 대신 프로젝트 전용 Node 24.13.0을 자동 설치해 사용한다.
  첫 빌드 후 `.gradle/nodejs`에 캐시되며 전역 Node 설정은 변경하지 않는다.
  전용 Node 버전은 `.node-version` 한 곳에서 관리하며 `npm start`도 같은 파일을 사용한다.
- 운영 JAR 실행 환경은 기존과 동일하게 Java 17이다.
- `package-lock.json`을 함께 보관한다. Gradle은 `.gradle/frontend-build`에 소스와 의존성을 따로 준비해
  `npm ci --include=dev --include=optional`로 설치한다. `bootJar`가 개발용 `node_modules`를 수정하지 않으므로
  개발 서버를 실행한 상태에서도 빌드할 수 있다.
- `.npmrc`가 개발 도구와 플랫폼별 선택 의존성을 포함한다. 설치 직후 Vite 네이티브 모듈 로딩도 검사한다.

`issue-system-frontend` 폴더에서 기존 명령을 그대로 사용한다.

```powershell
npm start
```

현재 Node가 지원 버전이면 그대로 사용하고, Node 20 등 미지원 버전이면 프로젝트 전용 Node로 실행한다.
전용 Node가 아직 없으면 Gradle의 `nodeSetup`으로 자동 준비한다. 이 최초 준비에는 Gradle을
실행할 수 있는 Java 환경과 Node 다운로드를 위한 네트워크가 필요하다.
`npm start -- --port 3001` 같은 Vite 옵션도 그대로 전달한다.
의존성이 누락되거나 손상되었으면 별도 프로세스로 검사한 뒤 전용 Node의 npm으로 자동 복구한다.
검사 프로세스가 종료된 후 설치하므로 검사 중 읽은 네이티브 모듈이 재설치를 방해하지 않는다.

IntelliJ의 Gradle 창 또는 프로젝트 루트에서도 다음 명령을 사용할 수 있다.

```powershell
.\gradlew.bat classes
.\gradlew.bat npmStart
.\gradlew.bat npmTest
.\gradlew.bat npmAudit
```

각각 백엔드 실행 준비, 개발 서버, 프론트 테스트, 취약점 검사다.
Gradle 설정을 변경한 후 IntelliJ에서 Gradle 프로젝트를 새로고침한다.

IntelliJ 상단 실행 구성에서 **프론트엔드 개발 서버**를 선택하고 실행하면 된다.
공유 실행 구성은 `../.run/Frontend.run.xml`에 있으며 `npmStart` Gradle 작업을 실행한다.
Gradle 창에서도 `Tasks > frontend > npmStart`로 실행할 수 있다.

### 설치·빌드·테스트를 npm으로 직접 실행하는 경우

`gradle-wrapper.properties`는 Gradle 버전을 지정하며 전역 Node 버전을 바꾸지 않는다.
`npm start`는 Node를 자동 선택하지만, `npm ci`, `npm test`, `npm run build`를 직접 실행하려면
터미널/IntelliJ의 Node 런타임을 지원 버전으로 지정한다. 지원하지 않는 버전의 설치·빌드·테스트는 중단한다.

전용 Node를 준비한 Windows 환경에서는 IntelliJ npm 실행 구성의 `Node runtime`(또는
`Node interpreter`)에 프로젝트의 `.gradle\nodejs\node-v24.13.0-win-x64\node.exe`를 지정할 수 있다.

PowerShell에서는 프론트 폴더에서 현재 터미널의 PATH를 설정할 수 있다.

```powershell
$env:Path = "$(Resolve-Path ..\.gradle\nodejs\node-v24.13.0-win-x64);$env:Path"
node --version
npm.cmd ci
```

`npm.cmd`로 실행하면 PowerShell의 `npm.ps1` 실행 정책에 영향을 받지 않는다.

아래 npm 명령을 직접 사용할 때는 `issue-system-frontend` 디렉터리에서 실행한다.

```sh
npm ci
npm start
npm test
npm run build
npm run audit
```

`Cannot find package 'vite'` 또는 `Cannot find native binding` 오류가 발생한 개발 환경은
`npm start`로 다시 실행하면 복구된다. 직접 다시 설치하려면 개발 서버를 종료한 후 위 폴더에서
지원 버전의 Node로 `npm ci --include=dev --include=optional`을 실행한다. 잠금 파일은 유지한다.
Gradle의 `npmInstall`은 빌드 전용 `.gradle/frontend-build/node_modules`에만 설치한다.
Windows의 `node_modules`를 Linux로 복사하지 않는다. Docker 빌드에서도 해당 폴더는 제외한다.

`npm start`는 기본 `http://127.0.0.1:3000`에서 실행한다. 기존처럼 같은 호스트의
8080 포트에 있는 Spring Boot API를 호출한다. `HOST`, `PORT`, `BROWSER=none` 환경 변수도 지원한다.
`npm run test:watch`는 테스트 감시 실행, `npm run lint`는 정적 검사 명령이다.
기존 Jest 전용 옵션인 `--watchAll=false`, `--runInBand`는 사용하지 않는다.

## 배포

`npm run build` 결과는 기존과 같은 `build` 폴더와 `/static/` 경로를 사용한다.
Gradle 빌드 결과는 `.gradle/frontend-build/build`에 생성한 뒤 백엔드 정적 리소스로 복사한다.
상위 디렉터리에서 `./gradlew build` 또는 Windows의 `.\gradlew.bat build`를 실행하면
설치·정적 검사·프론트 빌드·JAR 패키징이 함께 실행된다. 운영 API는 기존처럼 같은 도메인을 사용한다.
DB SQL과 검색 함수는 이 의존성 교체로 변경되지 않는다.

Tailwind 3과 기존 CSS, Browserslist 설정을 유지한다. JavaScript 변환 목표는 보수적인 ES2015로 지정했다.
JSX를 포함한 진입 파일·App·App 테스트만 `.jsx` 확장자로 바꾸고 기존 로직을 유지한다.

2026-10-27 기준 `npm run audit`는 개발 도구를 포함한 전체 의존성에서 **취약점 0건**이다.
감사 제외·심각도 필터·강제 버전 덮어쓰기는 사용하지 않는다. 이후 보안 공지에 따라 결과는 달라질 수 있다.

설정 참고: [Vite 빌드](https://vite.dev/guide/build.html),
[Vitest 마이그레이션](https://vitest.dev/guide/migration/),
[Gradle Node 도구 관리](https://github.com/node-gradle/gradle-node-plugin/blob/main/docs/usage.md).
