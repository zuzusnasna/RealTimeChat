# RealTimeChat

**Spring Boot · WebSocket/STOMP · MySQL** 기반의 실시간 채팅 서비스입니다.

회원가입과 세션 기반 로그인, 채팅방 생성, 메시지 실시간 전달 및 저장을 구현하고 있습니다. 이후 Redis 기반 분산 메시지 처리, 부하 테스트, 모니터링과 배포 자동화로 확장하는 백엔드 프로젝트입니다.

## 목차

- [프로젝트 개요](#프로젝트-개요)
- [기술 스택](#기술-스택)
- [구현 현황](#구현-현황)
- [시스템 동작 흐름](#시스템-동작-흐름)
- [API 명세](#api-명세)
- [로컬 실행 방법](#로컬-실행-방법)
- [다음 개발 계획](#다음-개발-계획)

## 프로젝트 개요

- **목표:** 실시간 채팅을 기반으로 인증, 데이터 저장, 접근 제어, 분산 처리와 운영 환경을 단계적으로 구현합니다.
- **실시간 통신:** WebSocket 연결 위에서 STOMP로 메시지를 전송하고 채팅방별 토픽을 구독합니다.
- **데이터 저장:** 사용자와 채팅 메시지를 MySQL에 저장합니다.
- **인증:** Spring Security와 HTTP 세션을 이용해 로그인 상태를 유지합니다.
- **설정 관리:** DB 접속 정보는 환경 변수로 관리하며 실제 비밀 정보는 저장소에 포함하지 않습니다.

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 21 |
| Backend | Spring Boot |
| Real-time communication | WebSocket, STOMP |
| Security | Spring Security, BCrypt, HTTP Session |
| Database | MySQL 8 |
| ORM | Spring Data JPA, Hibernate |
| Build | Gradle |
| Configuration | `.env`, `.env.example` |

## 구현 현황

상태는 로컬에서 직접 확인한 동작과 코드 구현 여부를 기준으로 정리합니다.

| 영역 | 기능 | 상태 |
|---|---|---|
| 프로젝트 기반 | Spring Boot 프로젝트 및 실행 환경 구성 | ✅ 완료 |
| 채팅방 | 채팅방 생성 및 목록 조회 | ✅ 확인 |
| 실시간 통신 | WebSocket/STOMP 메시지 송수신 | ✅ 확인 |
| 메시지 저장 | 채팅 메시지 MySQL 저장 및 채팅방별 이력 조회 | ✅ 확인 |
| 채팅 UI | 브라우저에서 채팅방 선택 및 대화 | ✅ 확인 |
| 인증 | 회원가입, 로그인, 로그아웃 및 BCrypt 비밀번호 해싱 | ✅ 확인 |
| 세션 | 새로고침 후 로그인 상태 유지 | ✅ 확인 |
| 채팅방 선택 상태 | 새로고침 후 마지막으로 선택한 채팅방 복원 | ✅ 확인 |
| WebSocket 보안 | 인증된 세션 사용자 기반 발신자 지정 | ✅ 확인 |
| 인증 접근 제어 | 로그아웃 후 채팅 REST API 및 WebSocket 연결 차단 | ✅ 확인 |
| 채팅방 권한 | 참여/비참여 사용자 구분 및 메시지 전송·구독·이력 조회 권한 검증 | 🟡 구현 완료, 로컬 테스트 필요 |
| 분산 처리 | Redis Pub/Sub 기반 서버 간 메시지 전달 | ⬜ 예정 |
| 성능 테스트 | k6 동시 접속·메시지 부하 테스트 | ⬜ 예정 |
| 모니터링 | Actuator, Prometheus, Grafana | ⬜ 예정 |
| 배포 자동화 | Docker 및 CI/CD 구성 | ⬜ 예정 |

> **현재 보안 범위:** 로그인한 사용자만 채팅 기능에 접근할 수 있고, 메시지 발신자는 인증된 세션에서 결정됩니다. 채팅방 생성자는 자동으로 참여자가 되며, 다른 로그인 사용자는 채팅방 목록에서 참여 버튼을 눌러 입장할 수 있습니다. 참여하지 않은 사용자의 메시지 이력 조회, STOMP 메시지 전송 및 토픽 구독은 서버에서 차단하도록 구현했습니다. 실제 적용 전에는 로컬에서 참여/비참여 계정 두 개로 검증해야 합니다.

## 시스템 동작 흐름

### 메시지 송수신

1. 로그인한 클라이언트가 HTTP 세션을 사용해 `/ws` WebSocket 엔드포인트에 연결합니다.
2. 클라이언트가 메시지 내용을 `/app/chat/{roomId}`로 전송합니다.
3. 서버는 인증된 사용자 이름을 발신자로 지정하고 메시지를 저장합니다.
4. 서버가 `/topic/rooms/{roomId}` 구독자에게 메시지를 전달합니다.
5. 채팅 화면은 REST API로 이전 메시지를 조회하며, 새로고침 후에는 마지막으로 선택한 채팅방을 복원합니다.

### 인증 흐름

1. 회원가입 시 비밀번호를 BCrypt로 해싱해 저장합니다.
2. 로그인에 성공하면 HTTP 세션으로 인증 상태를 유지합니다.
3. `GET /api/auth/me`로 현재 로그인 사용자 정보를 조회합니다.
4. 로그아웃하면 세션을 종료하며, 이후 인증이 필요한 채팅 API와 WebSocket 연결은 거부됩니다.

## API 명세

### 인증 API

| Method | Endpoint | 설명 |
|---|---|---|
| `POST` | `/api/auth/signup` | 회원가입 |
| `POST` | `/api/auth/login` | 로그인 및 세션 생성 |
| `GET` | `/api/auth/me` | 현재 로그인 사용자 조회 |
| `POST` | `/api/auth/logout` | 로그아웃 및 세션 종료 |

### 채팅 REST API

| Method | Endpoint | 설명 |
|---|---|---|
| `GET` | `/api/rooms` | 채팅방 목록 조회 |
| `POST` | `/api/rooms` | 채팅방 생성 (생성자는 자동 참여) |
| `POST` | `/api/rooms/{roomId}/join` | 채팅방 참여 |\n| `GET` | `/api/rooms/{roomId}/messages` | 참여 중인 채팅방의 최근 메시지 조회 |

### WebSocket / STOMP

| 구분 | 경로 | 설명 |
|---|---|---|
| CONNECT | `/ws` | 인증된 세션으로 WebSocket 연결 |
| SEND | `/app/chat/{roomId}` | 채팅 메시지 전송 |
| SUBSCRIBE | `/topic/rooms/{roomId}` | 채팅방 메시지 구독 |

## 로컬 실행 방법

### 1. 사전 준비

- JDK 21
- MySQL 8
- Git

### 2. 데이터베이스 생성

MySQL에서 데이터베이스를 생성합니다.

```sql
CREATE DATABASE realtime_chat
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

### 3. 환경 변수 설정

프로젝트 루트에서 `.env.example`을 참고해 `.env` 파일을 만들고 로컬 데이터베이스 접속 정보를 입력합니다.

예시:

```properties
DB_URL=jdbc:mysql://localhost:3306/realtime_chat?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=root
DB_PASSWORD=your_local_password
```

실제 필수 변수 이름과 항목은 저장소의 `.env.example`을 기준으로 확인하세요. 비밀번호가 포함된 `.env` 파일은 커밋하지 마세요.

### 4. 서버 실행

Windows PowerShell에서 프로젝트 루트로 이동해 실행합니다.

```powershell
.\\gradlew.bat bootRun
```

실행 후 브라우저에서 접속합니다.

- 로그인·회원가입: http://localhost:8080/login.html
- 채팅 화면: http://localhost:8080/chat.html

## 다음 개발 계획

1. **채팅방별 접근 권한 검증:** 참여하지 않은 사용자의 메시지 전송, 토픽 구독, 메시지 이력 조회를 차단합니다.
2. **Redis Pub/Sub:** 여러 애플리케이션 인스턴스 사이에서 채팅 메시지를 전달하도록 확장합니다.
3. **k6 부하 테스트:** 동시 접속 및 메시지 전송 부하를 측정하고 병목을 분석합니다.
4. **모니터링:** Spring Boot Actuator, Prometheus, Grafana로 애플리케이션 지표를 수집하고 시각화합니다.
5. **배포 자동화:** Docker와 CI/CD 파이프라인을 구성합니다.
