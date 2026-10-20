# RealTimeChat

**Spring Boot · WebSocket/STOMP · MySQL** 기반의 실시간 채팅 서비스입니다.

회원가입과 세션 기반 로그인부터 채팅 메시지의 실시간 전달·저장까지 구현하고, 이후 Redis를 활용한 다중 서버 메시지 처리와 부하 테스트, 모니터링 및 배포 자동화로 확장하는 백엔드 프로젝트입니다.

## 목차

- [프로젝트 개요](#프로젝트-개요)
- [기술 스택](#기술-스택)
- [구현 현황](#구현-현황)
- [시스템 동작 흐름](#시스템-동작-흐름)
- [API 명세](#api-명세)
- [로컬 실행 방법](#로컬-실행-방법)
- [로드맵](#로드맵)

## 프로젝트 개요

- **목표:** 실시간 채팅 기능을 기반으로 인증, 데이터 저장, 분산 처리, 성능 검증 및 운영 환경까지 단계적으로 구현합니다.
- **통신 방식:** WebSocket 연결 위에서 STOMP로 채팅 메시지를 전송하고 구독합니다.
- **데이터 저장:** 사용자와 채팅 메시지를 MySQL에 저장합니다.
- **설정 관리:** 데이터베이스 연결 정보는 환경 변수로 관리하며, 실제 비밀 정보는 저장소에 포함하지 않습니다.

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

완료 상태는 실제 로컬 동작 확인 결과를 기준으로 관리합니다.

| 영역 | 기능 | 상태 |
|---|---|---|
| 프로젝트 기반 | Spring Boot 프로젝트 및 실행 환경 구성 | ✅ 완료 |
| 실시간 통신 | WebSocket/STOMP 연결 및 메시지 송수신 | ✅ 확인 |
| 메시지 저장 | 채팅 메시지 MySQL 저장 | ✅ 확인 |
| 채팅방 | 채팅방 생성 및 목록 조회 | ✅ 확인 |
| 채팅 기록 | 채팅방별 최근 메시지 100개 조회 | ✅ 구현 |
| 채팅 UI | 브라우저에서 채팅방 선택 및 대화 | ✅ 확인 |
| 사용자 인증 | 회원가입, 로그인, 로그아웃, BCrypt 비밀번호 해싱 | ✅ 기본 흐름 확인 |
| 세션 관리 | 새로고침 후 로그인 유지 | ✅ 확인 |
| WebSocket 보안 | 세션 인증 연결, 서버 측 발신자 지정 및 채팅 API 인증 요구 | 🔄 구현됨 · 로컬 검증 필요 |
| 채팅방 권한 | 사용자별 참여·메시지 접근 권한 검증 | ⬜ 예정 |
| 분산 처리 | Redis Pub/Sub 기반 서버 간 메시지 전달 | ⬜ 예정 |
| 성능 테스트 | k6 동시 접속·메시지 부하 테스트 | ⬜ 예정 |
| 모니터링 | Actuator, Prometheus, Grafana | ⬜ 예정 |
| 배포 자동화 | Docker 및 CI/CD 구성 | ⬜ 예정 |

> **주의:** 회원가입·로그인·로그아웃의 기본 흐름은 로컬에서 확인했습니다. WebSocket 인증과 발신자 위조 방지 코드는 추가했으며 로컬 검증이 필요합니다. 채팅방별 사용자 참여 권한과 구독 권한 검증은 별도 후속 작업입니다. 현재 상태를 완성된 보안 구현으로 간주하지 않습니다.

## 시스템 동작 흐름

### 실시간 메시지

1. 클라이언트가 `/ws` 엔드포인트로 WebSocket 연결을 수립합니다.
2. 채팅 메시지를 `/app/chat/{roomId}`로 전송합니다.
3. 서버가 메시지를 처리하고 MySQL에 저장합니다.
4. 서버가 `/topic/rooms/{roomId}` 구독자에게 메시지를 전달합니다.
5. 클라이언트는 REST API를 통해 이전 메시지를 조회할 수 있습니다.

### 인증

1. 사용자가 회원가입 요청을 보내면 비밀번호를 BCrypt로 해싱해 저장합니다.
2. 로그인 성공 시 HTTP 세션을 생성해 인증 상태를 유지합니다.
3. `/api/auth/me`로 현재 로그인 사용자 정보를 조회할 수 있습니다.
4. 로그아웃 요청으로 세션을 종료합니다.

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
| `POST` | `/api/rooms` | 채팅방 생성 |
| `GET` | `/api/rooms/{roomId}/messages` | 특정 채팅방의 최근 메시지 조회 |

### WebSocket / STOMP

| 구분 | 경로 | 설명 |
|---|---|---|
| CONNECT | `/ws` | WebSocket 연결 |
| SEND | `/app/chat/{roomId}` | 특정 채팅방에 메시지 전송 |
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

프로젝트 루트의 `.env.example`을 참고해 `.env` 파일을 만들고 로컬 데이터베이스 접속 정보를 입력합니다.

예시:

```properties
DB_URL=jdbc:mysql://localhost:3306/realtime_chat?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=root
DB_PASSWORD=your_local_password
```

실제 환경 변수 이름과 필수 항목은 저장소의 `.env.example`을 기준으로 확인하세요. 실제 비밀번호가 포함된 `.env` 파일은 커밋하지 마세요.

### 4. 서버 실행

Windows PowerShell에서 프로젝트 루트로 이동한 뒤 실행합니다.

```powershell
.\gradlew.bat bootRun
```

서버 실행 후 브라우저에서 접속합니다.

- 로그인·회원가입: http://localhost:8080/login.html
- 채팅 화면: http://localhost:8080/chat.html
- 채팅방 목록 API: http://localhost:8080/api/rooms

## 로드맵

- [x] 실시간 메시지 송수신 및 MySQL 저장
- [x] 채팅방 생성·목록 조회 및 이전 메시지 조회
- [x] 회원가입·로그인·로그아웃 기본 흐름
- [ ] WebSocket 인증 및 서버 측 발신자 검증
- [ ] 채팅방별 접근 권한 검증
- [ ] Redis Pub/Sub 기반 다중 서버 메시지 전달
- [ ] k6 부하 테스트 및 병목 분석
- [ ] Actuator·Prometheus·Grafana 모니터링
- [ ] Docker 기반 배포 및 CI/CD 자동화
