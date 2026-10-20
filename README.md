# RealTimeChat

Spring Boot와 WebSocket을 기반으로 구현하는 실시간 채팅 서비스입니다. 기본적인 실시간 메시지 전송부터 시작해 사용자 인증, 분산 메시지 처리, 성능 테스트 및 모니터링까지 단계적으로 확장하는 것을 목표로 합니다.

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 21 |
| Backend | Spring Boot |
| Real-time communication | WebSocket, STOMP |
| Database | MySQL 8 |
| ORM | Spring Data JPA, Hibernate |
| Build | Gradle |
| Configuration | `.env`, `.env.example` |

## 주요 기능 구현 현황

기능을 구현할 때마다 이 표를 업데이트합니다. 완료 여부는 실제 구현 및 확인 상태에 맞춰 순차적으로 기록합니다.

| 순서 | 영역 | 주요 기능 | 상태 |
|---:|---|---|---|
| 1 | 프로젝트 기반 | Spring Boot 프로젝트 구성 및 실행 환경 설정 | ✅ 완료 |
| 2 | 실시간 통신 | WebSocket + STOMP 연결 및 메시지 송수신 | ✅ 완료 |
| 3 | 메시지 저장 | 채팅 메시지를 MySQL에 저장 | ✅ 완료 |
| 4 | 채팅방 | 채팅방 생성 및 목록 조회 API | ✅ 완료 |
| 5 | 채팅방별 메시지 | 채팅방 ID를 기준으로 메시지 전송 및 브로드캐스트 | ✅ 완료 |
| 6 | 채팅 기록 | 채팅방별 이전 메시지 조회 (최근 100개) | ✅ 완료 |
| 7 | 채팅 UI | 브라우저에서 채팅방 생성·선택 및 실시간 대화 테스트 | ✅ 완료 |
| 8 | 사용자 인증 | 회원가입, 세션 로그인, 로그아웃 API, BCrypt 비밀번호 해싱 및 로그인 화면 | 🔄 구현됨 · 로컬 검증 필요 |
| 9 | 채팅방 권한 | 사용자별 채팅방 참여 및 메시지 전송 권한 검증 | ⬜ 예정 |
| 10 | 인증 연동 | WebSocket 연결 및 메시지에 사용자 인증 적용 | ⬜ 예정 |
| 11 | 캐시·분산 처리 | Redis Pub/Sub으로 서버 간 메시지 전달 | ⬜ 예정 |
| 12 | 테스트 | k6를 활용한 동시 접속 및 메시지 부하 테스트 | ⬜ 예정 |
| 13 | 모니터링 | Spring Boot Actuator, Prometheus, Grafana 연동 | ⬜ 예정 |
| 14 | 배포 자동화 | Docker 기반 실행 환경 및 CI/CD 구성 | ⬜ 예정 |

> **상태 기준:** ✅ 완료 · 🔄 진행 중 · ⬜ 예정  
> 새 기능을 구현하고 동작을 확인한 뒤 상태를 갱신하고, 필요한 경우 항목과 순서를 추가합니다.

## 현재 구현된 API

| Method | Endpoint | 설명 |
|---|---|---|
| `POST` | `/api/auth/signup` | 회원가입 |
| `POST` | `/api/auth/login` | 로그인 및 세션 생성 |
| `GET` | `/api/auth/me` | 현재 로그인 사용자 조회 |
| `POST` | `/api/auth/logout` | 로그아웃 및 세션 종료 |
| `GET` | `/api/rooms` | 채팅방 목록 조회 |
| `POST` | `/api/rooms` | 채팅방 생성 |
| `GET` | `/api/rooms/{roomId}/messages` | 특정 채팅방의 최근 메시지 조회 |
| WebSocket / STOMP | `/ws` | WebSocket 연결 엔드포인트 |
| STOMP SEND | `/app/chat/{roomId}` | 특정 채팅방에 메시지 전송 |
| STOMP SUBSCRIBE | `/topic/rooms/{roomId}` | 특정 채팅방의 실시간 메시지 구독 |

### 메시지 흐름

1. 클라이언트가 `/ws`로 WebSocket 연결을 수립합니다.
2. 채팅방 메시지를 `/app/chat/{roomId}`로 전송합니다.
3. 서버는 메시지를 검증하고 MySQL에 저장합니다.
4. 저장된 메시지를 `/topic/rooms/{roomId}` 구독자에게 전달합니다.
5. 채팅방 기록 API로 이전 메시지를 조회할 수 있습니다.

## 로컬 실행

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

프로젝트 루트의 `.env.example`을 참고해 `.env` 파일을 만들고 실제 로컬 DB 접속 정보를 설정합니다.

예시:

```properties
DB_URL=jdbc:mysql://localhost:3306/realtime_chat?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=root
DB_PASSWORD=your_local_password
```

실제 비밀번호가 포함된 `.env` 파일은 저장소에 커밋하지 않습니다.

### 4. 서버 실행

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

서버 실행 후 채팅 화면을 엽니다.

- 채팅 UI: http://localhost:8080/chat.html
- 채팅방 목록: http://localhost:8080/api/rooms

## 개발 방향

이 프로젝트는 기능을 한 번에 많이 추가하기보다, 단계별로 구현하고 동작을 확인하며 확장합니다.

1. 회원가입 및 로그인
2. 채팅방 참여 권한과 WebSocket 인증
3. Redis Pub/Sub 기반 다중 서버 메시지 전달
4. k6 부하 테스트 및 병목 분석
5. 모니터링과 배포 자동화

각 단계를 완료할 때마다 **주요 기능 구현 현황** 표를 업데이트해 진행 상황과 검증된 기능을 함께 관리합니다.
