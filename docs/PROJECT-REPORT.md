# RealTimeChat 프로젝트 총정리

> 현재 저장소와 지금까지의 로컬·EC2 테스트 기록을 기준으로 정리했습니다. 측정값은 특정 시점의 결과이며, 미해결 항목은 완료된 기능과 구분합니다.

## 1. 프로젝트 개요

RealTimeChat은 Spring Boot와 React 기반의 실시간 채팅 서비스입니다. 세션 기반 인증, 채팅방 접근 제어, 메시지 영속화, WebSocket/STOMP 이벤트 전달, Redis Pub/Sub 기반 다중 인스턴스 중계를 구현합니다.

## 2. 구성 및 책임

| 구성 요소 | 역할 |
|---|---|
| React + Vite | 채팅 UI, 로그인/회원가입, 방 탐색, 메시지 및 읽음 상태 표시 |
| Spring Boot app-1/app-2 | REST API, 세션 인증, 채팅 도메인 로직, STOMP 연결 |
| MySQL 8 | 사용자, 채팅방, 메시지 및 읽음 상태 저장 |
| Redis Pub/Sub | 앱 인스턴스 사이 메시지·읽음 이벤트 중계 |
| Nginx | HTTP/WebSocket reverse proxy 및 앱 인스턴스 라우팅 |
| Actuator/Micrometer | 앱 상태와 메트릭 노출 |
| Prometheus/Grafana | 선택형 메트릭 수집과 시각화 |
| k6 | HTTP/WebSocket 및 교차 인스턴스 전달 부하 테스트 |
| GitHub Actions | PR 테스트와 main 기반 배포 자동화 |

### 메시지 흐름 개요

1. 인증된 클라이언트가 WebSocket/STOMP를 통해 메시지를 전송합니다.
2. 서버에서 사용자의 인증과 채팅방 접근 권한을 검증합니다.
3. 메시지를 MySQL에 저장합니다.
4. Redis Pub/Sub으로 이벤트를 발행하고 구독 중인 앱 인스턴스가 WebSocket 클라이언트에 전달합니다.
5. 클라이언트는 실시간 이벤트와 REST 초기 이력을 함께 표시합니다.

이 흐름은 기능의 큰 그림입니다. 여러 인스턴스에서 동시에 발행된 메시지의 엄격한 방 단위 순서를 보장한다고 의미하지는 않습니다.

## 3. 구현된 기능

- 회원가입·로그인·로그아웃 및 HTTP Session 기반 인증
- 인증 상태와 채팅방 참여 권한 확인
- 채팅방 생성·참여, 메시지 저장 및 이전 대화 조회
- WebSocket/STOMP 실시간 송수신
- Redis Pub/Sub 다중 인스턴스 메시지 및 읽음 상태 중계
- 사용자별 마지막 읽은 위치와 미읽음 인원 수
- 닉네임 변경 및 채팅 메시지에 현재 닉네임 표시
- 메시지 본문 URL 링크 처리
- 초기 이력과 실시간 이벤트 중복을 줄이기 위한 병합 처리
- 로컬 MySQL/Redis 전용 Compose 및 PowerShell 시작·종료 스크립트
- 선택형 Prometheus/Grafana 모니터링

## 4. 성능 및 운영 최적화 이력

### DB 잠금 경합 완화

메시지 저장 경로에서 채팅방 행의 비관적 쓰기 잠금(PESSIMISTIC_WRITE)과 findByIdForUpdate() 호출을 제거했습니다. 같은 방에 대한 동시 전송을 직렬화하던 경로를 없애 지연을 줄이는 데 초점을 맞췄습니다. 성능 최적화를 이유로 해당 잠금을 다시 추가하지 않는 것이 현재 결정입니다.

### EC2 자원 사용량 관리

- Prometheus/Grafana를 Compose의 monitoring 프로필로 분리해 기본 운영 구성에서 제외했습니다.
- 앱 인스턴스별 JVM 메모리 옵션과 컨테이너 메모리 제한을 설정했습니다.
- HikariCP 최대 연결 풀 기본값을 인스턴스당 4개, 최소 유휴 연결을 1개로 제한했습니다.
- SQL 출력은 기본 비활성화하고 진단 시 환경 변수로 켤 수 있게 했습니다.
- 로컬 MySQL은 호스트 3307 포트를 사용해 Windows MySQL 서비스의 3306 포트와 분리했습니다.

### 기록된 k6 결과

| 실행 환경 | 조건 | 전송 / 수신 | 평균 전달 시간 | p95 | 최대 |
|---|---|---:|---:|---:|---:|
| 로컬 | 2 VU, 30초, 250ms 간격 | 232 / 232 | 26.42ms | 39.69ms | 494ms |
| EC2 | 2 VU, 30초, 250ms 간격 | 232 / 232 | 27.3ms | 68.69ms | 277ms |
| EC2 | 2 VU, 60초, 100ms 간격 | 1158 / 1158 | 18.96ms | 34ms | 171ms |

이 수치는 당시 실행 기록입니다. 테스트 데이터, 자원 상태, 인스턴스 라우팅, 실행 시점이 달라지면 결과도 달라질 수 있습니다.

## 5. 메시지 순서 문제 — 미해결

강한 EC2 테스트에서 1158건 전송·수신, 평균 18.96ms, p95 34ms, 최대 171ms가 기록됐지만 순서 역전 6건과 도착 시점 sequence gap 6건도 관측됐습니다.

Redis listener를 단일 스레드 executor로 직렬화하는 시도는 로컬 테스트에서 성능 회귀를 보여 되돌렸습니다.

- 전송 1117건, 수신 1080건
- 평균 전달 시간 91.98ms, p95 567.74ms, 최대 2.19초
- 관측된 순서 역전 23건, gap 21건

따라서 단일 스레드 listener는 현재 해결책으로 채택하지 않습니다. 또한 gap 카운터는 메시지가 늦게 도착해도 이전 관측치를 취소하지 않을 수 있어, 실제 유실과 늦은 도착을 구분하는 테스트 로직 개선이 필요합니다. DB ID 순서가 동시 발행 이벤트의 전역 순서를 보장한다고 볼 수도 없습니다.

### 다음 조사 순서

1. k6 테스트 코드에서 sequence gap, 중복, 역전의 계산 방식을 검토합니다.
2. STOMP 수신 → DB 저장/트랜잭션 → Redis 발행 → Redis listener → WebSocket 전달의 각 단계별 시간을 추적합니다.
3. 한 인스턴스와 다중 인스턴스 테스트를 분리해 재현 조건을 좁힙니다.
4. 방 단위 순서 보장 방식을 설계하고, 동일 부하에서 지연·누락·중복을 함께 비교합니다.
5. 기능 브랜치에서 검증한 뒤 사용자 확인을 거쳐 병합합니다.

## 6. 설정 파일 정리 원칙

| 파일 | 책임 |
|---|---|
| docker-compose.yml | 운영/EC2 앱·DB·Redis·Nginx 및 선택형 모니터링 |
| docker-compose.local.yml | 로컬 개발용 MySQL·Redis만 실행 |
| src/main/resources/application.properties | 공통 Spring 설정 및 환경 변수 기본값 |
| src/main/resources/application-local.properties | 로컬 프로필의 DB/Redis 접속값 |
| start-local.ps1 / stop-local.ps1 | Windows 로컬 개발 프로세스 실행·종료 |
| build.gradle | Java 의존성, 프론트엔드 설치/빌드, 테스트 설정 |
| gradle/wrapper/ 및 gradlew* | Gradle Wrapper. 생성 파일은 수동 편집하지 않음 |
| .github/workflows/ci.yml | PR 테스트 |
| .github/workflows/deploy-ec2.yml | main push 기반 EC2 배포 |
| .github/workflows/deploy.yml | 기존 Windows Compose 작업을 위한 수동 실행 workflow |

로컬과 운영 설정은 실행 목적과 포트가 다르므로 하나로 합치지 않습니다. 비밀번호와 개인 키는 저장소에 넣지 않고 환경 변수나 GitHub Secrets로 전달합니다. 캐시·빌드 산출물·임시 테스트 파일은 추적하지 않습니다.

## 7. 문서 안내

- [README](../README.md): 빠른 소개와 실행 시작점
- [개선 이력](PROJECT-IMPROVEMENTS.md): 기능 개선, 성능 수치, 되돌린 시도
- [저장소 구조](PROJECT-STRUCTURE.md): 폴더별 책임과 정리 기준
- [로컬 개발](LOCAL-DEVELOPMENT.md): PowerShell 실행 절차
- [Docker Compose](DOCKER.md): 컨테이너 실행·종료 및 문제 해결
- [모니터링](MONITORING.md): Prometheus/Grafana
- [k6](K6.md): 부하 테스트 실행 및 지표 해석
- [EC2 성능 분석](EC2-PERFORMANCE-ANALYSIS.md): 메모리·지연 관측과 검증 계획

## 8. 개발 원칙

- main에 직접 검증되지 않은 변경을 넣지 않습니다.
- 기능 브랜치에서 로컬 테스트 후 사용자 확인을 거쳐 병합합니다.
- 성능 변경은 같은 조건의 k6 결과와 호스트/컨테이너 지표로 비교합니다.
- docker compose down -v는 DB와 모니터링 데이터를 삭제할 수 있으므로 의도적으로 초기화할 때만 사용합니다.
- 메시지 순서 보장은 별도 검증이 통과하기 전까지 완료로 표시하지 않습니다.
