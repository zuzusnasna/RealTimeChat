# 로컬 개발 환경

기능 브랜치에서 개발하고 로컬 테스트한 뒤 `main`에 병합하는 것을 기준으로 합니다.

## 구조

Windows에서는 MySQL과 Redis를 Docker로 실행하고, Spring Boot는 IntelliJ 또는 Gradle로 직접 실행합니다.

```text
Windows

Spring Boot :8080
     │
     ├── MySQL Docker :3307 -> container :3306
     │
     └── Redis Docker :6379
```

Windows의 `MySQL80` 서비스는 사용할 필요가 없습니다.

## 1. 사전 준비

- Docker Desktop 실행
- JDK 21 설치
- Node.js/npm 설치
- 저장소를 `feature/*` 브랜치로 checkout

## 2. 로컬 DB/Redis 실행

프로젝트 루트에서:

```powershell
docker compose -f docker-compose.local.yml up -d
```

상태 확인:

```powershell
docker compose -f docker-compose.local.yml ps
```

MySQL과 Redis가 `healthy`가 될 때까지 잠시 기다립니다.

로그 확인:

```powershell
docker compose -f docker-compose.local.yml logs -f mysql redis
```

## 3. Spring Boot 실행

IntelliJ에서 실행할 경우 Run Configuration의 Active profiles에 `local`을 입력합니다.

또는 PowerShell에서:

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
.\gradlew.bat bootRun
```

서버:

```text
http://localhost:8080/ui/
```

Spring Boot의 `application-local.properties`가 Docker MySQL(`localhost:3307`)과 Docker Redis(`localhost:6379`)를 사용합니다.

## 4. 기능 테스트

기능 브랜치에서 다음 순서로 확인합니다.

1. 회원가입/로그인
2. 채팅방 생성 및 참여
3. WebSocket 연결
4. 메시지 송수신
5. Redis Pub/Sub 동작
6. 변경한 기능 테스트

기능에 문제가 있으면 `feature/*` 브랜치에서 수정하고 다시 테스트합니다.

## 5. 종료

Spring Boot는 IDE에서 종료하고 DB/Redis만 내릴 수 있습니다.

```powershell
docker compose -f docker-compose.local.yml down
```

컨테이너를 내려도 데이터는 Docker volume에 남습니다.

로컬 DB 데이터를 완전히 초기화해야 할 때만:

```powershell
docker compose -f docker-compose.local.yml down -v
```

## 6. 일반적인 기능 개발 흐름

```text
main
 │
 └── feature/기능명
       │
       ├── 로컬 checkout
       ├── Docker MySQL/Redis 실행
       ├── Spring Boot local 프로필 실행
       ├── 기능 테스트
       │
       └── 테스트 성공
              │
              ▼
           main 병합
              │
              ▼
        GitHub Actions
              │
              ▼
          EC2 자동 배포
```

운영용 `docker-compose.yml`은 수정하지 않습니다. 로컬 인프라는 `docker-compose.local.yml`로 분리합니다.
