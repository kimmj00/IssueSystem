# 최초 DB 구축 순서

PostgreSQL 13 이상을 기준으로 한다. 아래 예시는 앱과 DB가 같은 서버에 있고,
DB 포트가 `25432`, DB 이름이 `issue_system`, 앱 DB 계정이 `issue`인 경우다.
PostgreSQL 서비스와 `pg_trgm` 확장 패키지(postgresql-contrib)는 먼저 설치되어 있어야 한다.

업무 테이블·인덱스·검색 함수·트리거·검색 데이터 반영은 **`table-schema.sql` 하나로 실행**한다.
계정/DB 생성과 인증 파일 설정은 DB 관리자 단계에서 먼저 수행한다.
앱 로그인 계정과 PostgreSQL 접속 계정은 별개다.

## 0. PostgreSQL 설치와 수신 포트 준비 — 서버 관리자

아래 명령들은 DB 서비스가 이미 `25432` 포트에서 실행 중인 것을 전제로 한다.
새 설치의 기본 포트가 `5432`라면 `postgresql.conf`에서 다음 값을 설정하고,
설치된 PostgreSQL 서비스를 기동하거나 재시작한다.

```conf
listen_addresses = '127.0.0.1'
port = 25432
```

```bash
ss -lnt | grep ':25432'
```

`127.0.0.1:25432` 수신이 확인되면 다음 단계로 진행한다. 다른 포트를 사용하려면
이 문서의 접속 명령과 앱의 JDBC 주소에도 동일한 포트를 지정한다.

## 1. DB 계정과 빈 DB 생성 — DB 관리자

최초 구축 시 한 번 실행한다. 이미 존재하는 계정/DB에 다시 실행하지 않는다.
첫 번째 명령은 암호를 물어보므로 단독 실행하고, 이 암호를 앱의 DB 암호로 사용한다.

```bash
sudo -u postgres createuser -p 25432 --login --pwprompt issue
```

```bash
sudo -u postgres createdb -p 25432 --owner=issue --encoding=UTF8 --template=template0 issue_system
sudo -u postgres psql -X -p 25432 -d issue_system -v ON_ERROR_STOP=1 \
  -c 'ALTER ROLE issue IN DATABASE issue_system SET search_path TO issue, public;' \
  -c 'CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;'
```

`issue`가 DB 소유자이며, 초기 SQL도 같은 계정으로 실행해야 테이블/함수의 소유자가 일치한다.
이후 JAR의 검색 함수 자동 업데이트도 이 계정으로 처리된다.

## 2. 로컬 비밀번호 인증 설정 — DB 관리자

실제 사용 중인 인증 파일 경로를 확인한다.

```bash
sudo -u postgres psql -X -p 25432 -d issue_system -c 'SHOW hba_file;'
```

출력된 `pg_hba.conf`에서 기존 로컬 `ident`/기타 일치 규칙보다 위에 다음 한 줄을 추가한다.
DB와 계정을 `issue_system` / `issue`로 제한한 IPv4 로컬 접속 규칙이다.

```conf
host    issue_system    issue    127.0.0.1/32    md5
```

설정을 다시 읽는다. DB 재시작은 필요 없다.

```bash
sudo -u postgres psql -X -p 25432 -d issue_system -c 'SELECT pg_reload_conf();'
```

PostgreSQL 13에서 `md5` 규칙은 기존 MD5 암호와 호환되며, 저장된 암호가 SCRAM이면 SCRAM 인증을 사용한다.
수신 주소가 `127.0.0.1`/`::1`뿐인 서버는 앱 주소도 `127.0.0.1`로 지정한다.

## 3. 접속 확인 — 앱을 실행하는 리눅스 계정

```bash
psql -X -h 127.0.0.1 -p 25432 -U issue -d issue_system -W \
  -c 'SELECT current_database(), current_user, inet_server_addr(), inet_server_port();'
```

`issue_system / issue / 127.0.0.1 / 25432`가 출력되면 초기 SQL을 실행한다.

## 4. 통합 초기 SQL 실행 — issue DB 계정

저장소의 `erd/table-schema.sql`을 서버 `/home/issue/erd/table-schema.sql`에 복사한 후 실행한다.

```bash
psql -X -h 127.0.0.1 -p 25432 -U issue -d issue_system -W \
  -v ON_ERROR_STOP=1 -f /home/issue/erd/table-schema.sql
```

파일 안에서 다음 순서로 처리한다.

1. 실행 계정 이름의 스키마(`issue`)와 검색 경로 준비
2. 업무 테이블과 외래 키
3. 기존 컬럼 보강 및 데이터 보정
4. 조회 인덱스
5. 인프라 값 정규화와 제약 조건
6. 테이블/컬럼 설명
7. 검색 테이블·최신 검색 함수·등록/수정/삭제 동기화 트리거
8. 기존 원문 검색 데이터 반영 — 원본 500행씩 커밋, 빈 DB는 즉시 완료
9. 통계 갱신과 테이블 12개·검색 함수 11개·원문 동기화 트리거 4개·단어 길이 트리거 1개 확인

`초기 구축 완료` 메시지와 `schema_name = issue`를 확인한다.
새 DB의 마지막 검색 문서 건수 결과는 0행이어도 정상이다.

`-f`로 파일을 실행하고 기본 자동 커밋을 사용한다. `-1`/`--single-transaction`이나 별도
`BEGIN`으로 파일 전체를 감싸면 500행 배치의 내부 커밋이 실패한다.
01~07은 한 트랜잭션으로 처리하고, 08은 배치별로 커밋한다.
`ON_ERROR_STOP=1` 때문에 오류가 발생하면 즉시 중단된다.

원문과 테이블을 삭제하는 초기화 스크립트는 아니다. 이미 데이터가 있는 DB에서 재실행할 때는
앱 쓰기를 중지하고 같은 DB 소유 계정으로 실행한다. 변경 없는 검색 문서는 분해를 생략한다.
다른 스키마의 기존 테이블을 자동으로 이동하거나, 별도 백업 데이터를 복원하지는 않는다.

## 5. 앱 설정과 실행

`application.yml`의 `deploy` 프로필 또는 실행 환경변수를 다음 접속 정보와 일치시킨다.

| 항목 | 값 |
|---|---|
| `spring.datasource.url` | `jdbc:postgresql://127.0.0.1:25432/issue_system` |
| `spring.datasource.username` | `issue` |
| `spring.datasource.password` | 1단계에서 설정한 DB 계정 암호 |
| `spring.jpa.hibernate.ddl-auto` | `validate` |

JAR에 포함된 설정을 수정했다면 다시 빌드해 배포한다. 실행 옵션이나 환경변수에 지정한
DB 주소/암호가 있으면 해당 값도 일치시킨다. 업로드 경로는 앱 실행 계정이 쓸 수 있어야 한다.

```bash
java -jar /home/issue/app/issue_system.jar --spring.profiles.active=deploy
```

`admin` 앱 계정은 첫 앱 기동 시 `AdminAccountInitializer`가 생성한다. SQL에 로그인 계정을
중복 삽입하지 않는다. 앱에서 최초 로그인 후 관리자 암호를 변경한다.
등록한 글은 동기화 트리거로 즉시 검색에 반영된다.

## 기존 서버의 일반 업데이트

위 순서는 최초 DB 구축용이다. DB가 구축된 기존 서버는 새 JAR 교체 후 재시작하면 된다.
`SearchFunctionInitializer`가 JAR의 `db/search-functions.sql` 버전을 확인해 검색 구조와 함수를 갱신하며,
같은 버전은 건너뛴다. 동시 기동 시 DB 잠금으로 중복 갱신을 막고 교체를 한 트랜잭션으로 처리한다.
첫 적용 시 `app_search_posting.word_length`와 `char_signature`를 기존 단어 정보로 채우고
글 종류·ID·필드·길이·글자 정보 인덱스 `idx_app_search_posting_scope_terms`를 생성한다.
이 작업은 기존 연결 건수에 따라 기동 시간이 늘어나며, `검색 구조 업데이트 시작`과
`검색 함수 업데이트 완료` 로그에서 진행과 소요 시간을 확인한다. 이후 기동에는 반복하지 않는다.
기존 연결을 채운 경우 커밋 후 VACUUM으로 인덱스를 정리한다.
검색 구조 준비가 완료된 뒤 HTTP 서버를 시작하여 갱신 중 요청이 처리되지 않게 한다.
등록·수정은 길이·글자 정보도 갱신하며, 이전 4열 INSERT는 길이 보정 트리거로 지원한다.
검색 함수 안에서만 일반 실행계획을 재사용하며, 호출이 끝나면 연결의 원래 계획 설정으로 복원한다.
검색 호출 안에서는 JIT를 끄고 검색어 길이 경계를 한 번 계산한다.
제목·내용 모두 필터를 통과한 글의 연결에서 단어 길이로 먼저 좁힌 뒤,
글자 시그니처로 무관한 연결을 더 제외한 뒤 남은 단어만 기본키로 조회한다. 공용 사전 크기나 압축 본문 크기로 검색 경로를 추측하지 않는다.
오타 평가는 통과 가능한 거리의 범위에서 수행하며 기존 일치 점수와 인접 문자 교환 규칙을 유지한다.
DB 계정은 검색 함수의 소유/변경 권한이 있어야 하며, H2 테스트는 이 갱신을 건너뛴다.

기동할 때마다 `검색 엔진 준비` 로그에 DB·스키마·PostgreSQL 버전·검색 함수 버전과
`scopeIndexReady=true`를 기록한다. 검색 범위 인덱스가 없거나 유효하지 않으면 기동을 중단한다.
통합검색의 제목·내용 검색 단계가 1초를 넘으면 `검색 엔진 지연` 로그에 대상 종류, TITLE/CONTENT/ANY,
검색어 길이, 허용 ID 수, 반환 건수, 함수 처리 시간과 DB 프로세스 번호를 기록한다.
원문 검색어와 필터 값은 기록하지 않는다. 제목과 내용 동시 검색 및 업무 두 종류의 비용을 구분할 수 있다.
이 로그는 `app.logging.query.enabled=true`일 때 수집하며, 추가 진단 SQL 실행은 필요 없다.
함수 내부의 각 연산 시간이나 서버 자원 대기 시간까지 나타내는 로그는 아니다.

초기 구축 SQL은 `table-schema.sql` 하나를 사용한다.

## 검색 검증 — 개발용

검색은 제목/내용 AND, 오타·공백·부분 일치, 관련도 정렬, 필터, 전체 건수와 페이지 처리를 유지한다.
등록·수정·삭제는 검색 동기화 트리거로 반영한다.
실제 검색 회귀 테스트는 초기 SQL을 적용한 전용 PostgreSQL DB에서 실행한다.
아래 예시는 `search_test` 계정이 소유한 동일 이름의 전용 DB를 사용한다.

```powershell
$env:SEARCH_TEST_POSTGRES_URL = 'jdbc:postgresql://127.0.0.1:55439/search_test?currentSchema=search_test,public'
$env:SEARCH_TEST_POSTGRES_USER = 'search_test'
# 암호가 필요한 전용 DB는 SEARCH_TEST_POSTGRES_PASSWORD도 설정한다.
.\gradlew.bat test -x copyFrontendBuild -x npmBuild -x npmInstall -x cleanFrontendStatic
```

PostgreSQL 환경 변수가 없으면 해당 통합 테스트는 건너뛴다.
120만 단어·작업이력 18,000건을 사용하는 대용량 테스트는 `SEARCH_PERFORMANCE_TEST=true`로
별도 활성화한다. 같은 설정으로 작업이력 24,000건의 제목+내용 동시 검색도 검증한다.
PostgreSQL이 계획을 재사용한 뒤 느려지는 경우를 놓치지 않도록 `force_generic_plan` 조건을 포함한다.
일반 통합 테스트는 오타·공백·특수문자·검색 범위별 결과와 점수를 모든 단어를 평가하는 기준 쿼리와 비교한다.
대용량 테스트에는 내용만 검색, 제목+내용 동시 검색, 반복 글자와 큰 본문 끝의 오타 단어도 포함한다.
글자 순서가 서로 다른 단어 1,200개를 담은 긴 본문 30건도 기준 결과·점수와 비교한다.
대상 15건에 긴 단어 연결 18만 개가 있고 공용 사전에 무관한 긴 단어가 많은 상태에서
두 글자 내용 검색의 정확 일치·오타 점수도 검증한다. 기존 4열 구조 업그레이드와 재적용도 검증한다.
제목과 2~3글자 검색은 범위 인덱스를 직접 사용한다. 4글자 이상 내용·전체 검색에서만
원문을 제외한 단어 해시를 최대 129개 조사하고, 128개 이하면 그 전체 집합을 단어 인덱스로 찾는다.
조사 상한에 닿으면 범위 인덱스로 전환한다. 실제 검색 후보나 전체 건수에 상한을 두지 않는다.
테스트 URL에는 전용 DB를 지정한다.

참고: [PostgreSQL 인증 규칙](https://www.postgresql.org/docs/13/auth-pg-hba-conf.html),
[암호 인증](https://www.postgresql.org/docs/13/auth-password.html),
[프로시저의 트랜잭션 처리](https://www.postgresql.org/docs/13/plpgsql-transactions.html).
