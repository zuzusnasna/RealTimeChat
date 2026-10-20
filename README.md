# RealTimeChat

**Java 21 · Spring Boot · React · WebSocket/STOMP · MySQL · Redis Pub/Sub · Docker · Prometheus · Grafana**

Spring Boot 기반 실시간 채팅 서비스입니다. 세션 인증과 채팅방 권한 검증을 구현하고, Redis Pub/Sub 및 Docker Compose 다중 인스턴스 환경에서 서버 간 메시지 전달을 확인합니다. Prometheus와 Grafana로 두 인스턴스의 애플리케이션 메트릭을 수집·관찰합니다.

## 주요 기능

- 회원가입·로그인·로그아웃 및 HTTP Session 인증
- 채팅방 생성·참여, 메시지 저장 및 이력 조회
- WebSocket/STOMP 기반 실시간 메시지 송수신
- 서버 측 채팅방 참여 권한 검증 및 인증 사용자 기반 발신자 처리
- Redis Pub/Sub 기반 Spring Boot 인스턴스 간 메시지 중계
- Docker Compose 기반 MySQL·Redis·Spring Boot 2개 인스턴스 실행
- Prometheus 기반 메트릭 수집 및 Grafana 모니터링 환경

## 기술 스택

| 구분 | 기술 |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Spring Security |
| Real-time | WebSocket, STOMP |
| Database | MySQL 8, Spring Data JPA, Hibernate |
| Messaging | Redis Pub/Sub |
| Frontend | React, React Router, Vite |
| Container | Docker, Docker Compose |
| Monitoring | Spring Boot Actuator, Micrometer, Prometheus, Grafana |

## 메시지 처리 구조

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

메시지는 MySQL에 저장되며, Redis Pub/Sub을 통해 각 Spring Boot 인스턴스에 전달됩니다. 서버는 채팅방 참여 여부를 검증하고 클라이언트가 임의로 지정한 발신자 정보를 신뢰하지 않습니다.

## 실행

Docker Desktop을 실행한 뒤 프로젝트 루트에서:

```powershell
docker compose up --build -d
```

- app-1: http://localhost:8080/ui/
- app-2: http://localhost:8081/ui/
- Grafana: http://localhost:3000
- Prometheus: http://localhost:9091

환경 설정, 로그 확인, 포트 충돌 해결 및 데이터 초기화 방법은 **[Docker Compose 사용 가이드](docs/DOCKER.md)** 를 참고하세요. 모니터링 구성과 지표 확인 방법은 **[Prometheus + Grafana 가이드](docs/MONITORING.md)** 를 참고하세요.

## 부하 테스트

[k6 설치](https://grafana.com/docs/k6/latest/set-up/install-k6/) 후 두 인스턴스에 대한 WebSocket 채팅 부하 테스트를 실행할 수 있습니다.

```powershell
k6 run -e BASE_URL_1=http://localhost:8080 -e BASE_URL_2=http://localhost:8081 k6/chat-load.js
```

테스트 설정과 지표 해석은 **[k6 부하 테스트 가이드](docs/K6.md)** 를 참고하세요.

## 구현 현황 및 로드맵

- [x] 세션 기반 인증 및 채팅방 권한 검증
- [x] WebSocket/STOMP 실시간 채팅 및 메시지 영속화
- [x] Redis Pub/Sub 서버 간 메시지 중계
- [x] Docker Compose 기반 다중 인스턴스 구성 및 로컬 검증
- [x] Prometheus 메트릭 수집 및 Grafana 연결 구성
- [x] 기본 Grafana 대시보드 자동 등록
- [ ] k6 기반 동시 접속·메시지 부하 테스트 확대
- [ ] Grafana 알림 규칙 및 대시보드 고도화
- [ ] GitHub Actions CI/CD 및 배포 자동화

<!-- CI/CD test -->
