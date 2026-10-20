# RealTimeChat

**Java 21 · Spring Boot · React · React Router · WebSocket/STOMP · MySQL · Redis Pub/Sub** 기반의 실시간 채팅 서비스입니다.

회원가입과 세션 인증부터 채팅방별 접근 제어까지 구현하고, Redis 기반 분산 메시지 처리와 부하 테스트를 통해 확장성과 운영 역량을 검증하는 백엔드 프로젝트입니다.

## 목차

- [프로젝트 목표](#프로젝트-목표)
- [기술 스택](#기술-스택)
- [기능 구현 현황](#기능-구현-현황)
- [메시지 처리 흐름](#메시지-처리-흐름)
- [API 명세](#api-명세)
- [로컬 실행](#로컬-실행)
- [Redis 다중 인스턴스 검증](#redis-다중-인스턴스-검증)
- [개발 로드맵](#개발-로드맵)

## 프로젝트 목표

- **실시간 통신:** WebSocket 연결 위에서 STOMP를 이용해 채팅 메시지를 송수신합니다.
- **인증과 권한:** HTTP 세션으로 사용자를 인증하고, 채팅방 참여 여부를 서버에서 검증합니다.
- **데이터 영속화:** 사용자, 채팅방, 참여 정보와 메시지를 MySQL에 저장합니다.
- **확장성 검증:** Redis Pub/Sub, k6, 모니터링 및 배포 자동화를 단계적으로 도입합니다.
- **설정 관리:** 데이터베이스 접속 정보는 환경 변수로 관리하고 비밀 정보는 저장소에 커밋하지 않습니다.

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 |
| Security | Spring Security, BCrypt, HTTP Session |
| Real-time | WebSocket, STOMP |
| Database | MySQL 8 |
| Distributed messaging | Redis Pub/Sub |
| Persistence | Spring Data JPA, Hibernate |
| Validation | Jakarta Bean Validation |
| Frontend | React, React Router, Vite |
| Frontend build | npm, Gradle 연동 |
| Monitoring dependency | Spring Boot Actuator |
| Configuration | `.env`, `.env.example` |

## 기능 구현 현황

| 영역 | 기능 | 상태 |
|---|---|---|
| 채팅 | 채팅방 생성 및 목록 조회 | 구현 |
| 실시간 통신 | WebSocket/STOMP 메시지 송수신 | 로컬 확인 |
| 메시지 | 메시지 저장 및 채팅방별 이력 조회 | 로컬 확인 |
| 인증 | 회원가입, 로그인, 로그아웃, BCrypt 비밀번호 해싱 | 로컬 확인 |
| 세션 | 새로고침 후 로그인 상태 유지 | 로컬 확인 |
| UI | 새로고침 후 마지막 선택 채팅방 복원 | 로컬 확인 |
| 발신자 검증 | 클라이언트가 보낸 발신자 대신 인증된 세션 사용자 사용 | 로컬 확인 |
| 인증 접근 제어 | 로그아웃 후 채팅 REST API 및 WebSocket 접근 차단 | 로컬 확인 |
| 채팅방 권한 | 참여자만 이력 조회·메시지 전송·토픽 구독 가능하도록 서버에서 검증 | 구현 완료 |
| 분산 메시징 | Redis Pub/Sub 기반 서버 간 메시지 중계 | **구현 및 로컬 검증 완료** |
| 성능 테스트 | k6 동시 접속·메시지 부하 테스트 | 예정 |
| 모니터링 | Actuator 지표 노출 및 Prometheus/Grafana 연동 | 예정 |
| 배포 | Docker 및 CI/CD | 예정 |

## 메시지 처리 흐름

### Redis Pub/Sub 활성화 시

1. 사용자가 로그인하면 서버가 HTTP 세션으로 인증 상태를 유지합니다.
2. 클라이언트가 `/ws` 엔드포인트에 연결합니다.
3. 채팅방 참여자가 `/app/chat/{roomId}`로 메시지를 전송합니다.
4. 서버는 세션에서 인증된 사용자 이름을 사용해 메시지를 저장합니다.
5. `CHAT_REDIS_ENABLED=true`이면 저장된 메시지를 Redis Pub/Sub 채널 `realtime-chat:messages`로 발행합니다.
6. 각 Spring Boot 인스턴스가 Redis 채널을 구독합니다.
7. 각 인스턴스는 수신한 메시지를 자신에게 연결된 클라이언트에 `/topic/rooms/{roomId}`로 전달합니다.

### Redis 비활성화 시

`CHAT_REDIS_ENABLED=false`이면 Redis를 사용하지 않고 기존 인프로세스 브로커를 통해 해당 서버에 연결된 클라이언트에게 메시지를 전달합니다.

### 채팅방 권한 확인

- **REST:** 채팅방 메시지 이력을 조회할 때 로그인 사용자와 참여 정보를 확인합니다.
- **STOMP SEND:** `/app/chat/{roomId}`로 메시지를 보내기 전에 참여 여부를 확인합니다.
- **STOMP SUBSCRIBE:** `/topic/rooms/{roomId}` 구독 요청 전에 참여 여부를 확인합니다.
- **발신자:** 클라이언트가 임의로 지정한 발신자 이름을 신뢰하지 않고 인증된 세션의 사용자 이름을 사용합니다.

## API 명세

모든 채팅 API는 로그인 세션이 필요합니다.

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
| `GET` | `/api/rooms` | 채팅방 목록 및 참여 여부 조회 |
| `POST` | `/api/rooms` | 채팅방 생성; 생성자는 자동 참여 |
| `POST` | `/api/rooms/{roomId}/join` | 채팅방 참여 |
| `GET` | `/api/rooms/{roomId}/messages` | 참여 중인 채팅방의 최근 메시지 조회 |

### WebSocket / STOMP

| 동작 | 경로 | 설명 |
|---|---|---|
| CONNECT | `/ws` | 로그인 세션을 이용한 WebSocket 연결 |
| SEND | `/app/chat/{roomId}` | 채팅 메시지 전송 |
| SUBSCRIBE | `/topic/rooms/{roomId}` | 채팅방 메시지 구독 |

메시지 전송 시 클라이언트는 발신자 이름이 아닌 메시지 내용만 전송합니다.

```json
{
  "content": "안녕하세요!"
}
```

## 로컬 실행

### 사전 준비

- JDK 21
- MySQL 8
- Redis 7
- Git
- Node.js 및 npm

### 1. 저장소 내려받기

```powershell
git clone https://github.com/zuzusnasna/RealTimeChat.git
cd RealTimeChat
```

### 2. 데이터베이스 생성

```sql
CREATE DATABASE realtime_chat
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

### 3. 환경 변수 설정

프로젝트 루트의 `.env.example`을 참고해 `.env` 파일을 생성합니다.

```properties
DB_URL=jdbc:mysql://localhost:3306/realtime_chat?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=root
DB_PASSWORD=your_mysql_password

CHAT_REDIS_ENABLED=true
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
```

실제 비밀번호가 들어 있는 `.env` 파일은 커밋하지 마세요.

### 4. 단일 서버 실행

```powershell
.\gradlew.bat bootRun
```

기본 포트는 `8080`입니다.

프론트엔드 개발 서버를 별도로 실행하려면:

```powershell
cd frontend
npm install
npm run dev
```

Vite 기본 주소는 `http://localhost:5173/ui/#/login`입니다.

### 5. 테스트 실행

```powershell
.\gradlew.bat clean test
```

## Redis 다중 인스턴스 검증

Redis Pub/Sub가 실제로 서버 간 메시지를 중계하는지 확인하려면 **백엔드 인스턴스 2개**를 실행해야 합니다. 브라우저 탭 2개만 실행하는 것은 동일한 서버에 접속하는 것이므로 다중 인스턴스 검증이 아닙니다.

### 백엔드

터미널 1:

```powershell
.\gradlew.bat bootRun --args="--server.port=8080"
```

터미널 2:

```powershell
.\gradlew.bat bootRun --args="--server.port=8081"
```

두 인스턴스 모두 동일한 Redis(`localhost:6379`)를 사용합니다.

### 프론트엔드

터미널 3 — 서버 A(`8080`) 연결:

```powershell
cd frontend
$env:BACKEND_URL="http://localhost:8080"
$env:FRONTEND_PORT="5173"
npm run dev
```

터미널 4 — 서버 B(`8081`) 연결:

```powershell
cd frontend
$env:BACKEND_URL="http://localhost:8081"
$env:FRONTEND_PORT="5174"
npm run dev
```

- 클라이언트 A: `http://localhost:5173/ui/`
- 클라이언트 B: `http://localhost:5174/ui/`

### 검증 결과

클라이언트 A가 `8080` 인스턴스를 통해 메시지를 전송하고, 클라이언트 B가 `8081` 인스턴스를 통해 동일한 메시지를 수신하는 것을 확인했습니다.

즉, 다음 구조에서 **서로 다른 Spring Boot 인스턴스 사이의 실시간 메시지 전달이 정상 동작합니다.**

```text
Client A
   │
   ▼
Spring Boot :8080
   │
   ▼
Redis Pub/Sub :6379
   │
   ▼
Spring Boot :8081
   │
   ▼
Client B
```

## 개발 로드맵

- [x] 세션 기반 회원가입 및 로그인
- [x] 인증 사용자 기반 WebSocket 발신자 검증
- [x] 채팅방 참여 정보 및 서버 측 권한 검사 구현
- [x] Redis Pub/Sub 메시지 중계 및 다중 인스턴스 로컬 검증
- [ ] k6로 동시 접속 및 메시지 처리 성능 측정
- [ ] Actuator 지표 구성 및 Prometheus/Grafana 연동
- [ ] Docker 및 CI/CD 기반 배포 자동화
