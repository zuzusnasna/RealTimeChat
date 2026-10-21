# RealTimeChat

**Java 21 · Spring Boot · React · WebSocket/STOMP · MySQL · Redis Pub/Sub · Docker Compose · Prometheus · Grafana · k6**

Spring Boot와 React 기반의 실시간 채팅 서비스입니다. 세션 인증과 채팅방 접근 제어를 적용하고, Redis Pub/Sub으로 다중 Spring Boot 인스턴스 사이의 메시지와 읽음 상태 변경을 전달합니다.

## 주요 기능

- 회원가입·로그인·로그아웃 및 HTTP Session 인증
- 채팅방 생성·참여, 메시지 저장 및 이력 조회
- WebSocket/STOMP 기반 실시간 메시지 송수신
- 채팅방별 읽음 위치 저장 및 메시지별 미읽음 인원 수 표시
- 인증 사용자 기반 발신자 처리 및 채팅방 참여 권한 검증
- Redis Pub/Sub을 이용한 다중 인스턴스 메시지·읽음 상태 중계
- Docker Compose 기반 MySQL·Redis·Spring Boot 2개 인스턴스 구성
- Spring Boot Actuator·Micrometer 메트릭 수집, Prometheus·Grafana 모니터링
- k6를 이용한 WebSocket 연결, 메시지 송수신 및 인스턴스 간 전달 부하 테스트

## 기술 스택

| 구분 | 기술 |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Spring Security, Spring Data JPA |
| Frontend | React, React Router, Vite |
| Real-time | WebSocket, STOMP |
| Database / Messaging | MySQL 8, Redis Pub/Sub |
| Infrastructure | Docker, Docker Compose, Nginx |
| Monitoring / Test | Spring Boot Actuator, Micrometer, Prometheus, Grafana, k6 |
| CI/CD | GitHub Actions, self-hosted Windows runner |

## 아키텍처

```text
Browser
   │ HTTP / WebSocket
   ▼
Nginx (:80)
   ├── Spring Boot app-1
   └── Spring Boot app-2
          │          │
          ├── MySQL  ┤  메시지 영속화
          └── Redis ─┘  Pub/Sub 메시지·읽음 상태 중계

Prometheus ── app-1 / app-2 Actuator metrics
     │
     ▼
  Grafana
```

메시지는 MySQL에 저장됩니다. 각 Spring Boot 인스턴스는 Redis Pub/Sub을 통해 다른 인스턴스에 연결된 클라이언트에도 이벤트를 전달합니다. 인증 및 채팅방 참여 여부는 서버에서 검증합니다.

## 로컬 실행

Docker Desktop을 실행하고 프로젝트 루트에서 실행합니다. 먼저 `.env.example`을 참고해 `.env`를 준비하고 필수 비밀번호 값을 설정하세요.

```powershell
docker compose up --build -d
```

- 채팅 UI: http://localhost/ui/
- Grafana: http://localhost:3000 (로컬에서만 접근 가능)

Prometheus는 Docker 네트워크 내부에서 Grafana 및 애플리케이션과 통신하며 호스트 포트로 직접 공개하지 않습니다.

- [Docker Compose 실행·환경 설정 가이드](docs/DOCKER.md)
- [모니터링 구성 및 지표 가이드](docs/MONITORING.md)
- [k6 부하 테스트 가이드](docs/K6.md)

## 부하 테스트

```powershell
k6 run -e BASE_URL_1=http://localhost:8080 -e BASE_URL_2=http://localhost:8081 k6/chat-load.js
```

기본 시나리오는 테스트 사용자를 생성하고 두 인스턴스에 WebSocket/STOMP 클라이언트를 연결해 메시지 송수신 및 Redis를 통한 교차 인스턴스 전달을 확인합니다. 기존 로컬 검증에서는 51/51 checks 통과, HTTP 실패율 0%, 교차 인스턴스 전달 지연 p95 약 27ms를 확인했습니다. 이는 해당 로컬 실행 결과이며 운영 환경 성능을 의미하지 않습니다.

## CI/CD 및 배포 상태

- `main`에 push하면 GitHub Actions의 self-hosted Windows runner가 실행됩니다.
- 워크플로는 MySQL·Redis 상태를 확인한 뒤 app-1/app-2 이미지를 빌드하고 애플리케이션 컨테이너를 재생성합니다.
- 워크플로 성공 여부와 컨테이너 상태는 GitHub Actions 실행 로그에서 확인합니다.
- EC2 자동 배포는 별도 검증이 필요하며, Windows 로컬 자동 배포 성공만으로 EC2 배포 성공을 보장하지 않습니다.

## 구현 현황 및 다음 작업

- [x] 세션 인증, 로그아웃 및 채팅방 접근 제어
- [x] WebSocket/STOMP 실시간 채팅 및 메시지 영속화
- [x] Redis Pub/Sub 다중 인스턴스 메시지 중계
- [x] 메시지 읽음 처리 및 미읽음 인원 수 표시
- [x] Docker Compose 다중 인스턴스 로컬 구성
- [x] Prometheus·Grafana 모니터링 구성
- [x] 기본 k6 WebSocket 및 교차 인스턴스 전달 검증
- [x] `main` push 기반 Windows self-hosted runner 자동 빌드·재배포 워크플로 구성
- [ ] EC2 자동 배포 안정화 및 배포 후 헬스 체크 강화
- [ ] k6 동시 접속·메시지 부하 단계 확대
- [ ] Grafana 대시보드 및 알림 규칙 고도화
