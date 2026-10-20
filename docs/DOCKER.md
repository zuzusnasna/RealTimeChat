# Docker Compose 사용법

RealTimeChat은 Docker Compose로 MySQL, Redis, Spring Boot 인스턴스 2개를 함께 실행합니다.

## 사전 준비

- Docker Desktop 설치 및 실행
- 프로젝트 루트에서 명령 실행

## 실행

```powershell
docker compose up --build -d
```

첫 실행은 이미지 다운로드와 프론트엔드·백엔드 빌드로 시간이 걸릴 수 있습니다.

컨테이너 상태 확인:

```powershell
docker compose ps
```

정상 실행 시 MySQL과 Redis는 `healthy`, `app-1`과 `app-2`는 `Up` 상태여야 합니다.

## 접속 주소

- app-1: http://localhost:8080/ui/
- app-2: http://localhost:8081/ui/

서버 간 메시지 전달을 확인하려면 각 주소로 접속해 별도로 로그인한 뒤 같은 채팅방에서 메시지를 주고받습니다. Redis Pub/Sub이 두 Spring Boot 인스턴스 사이의 메시지를 중계합니다.

## 로그 확인

```powershell
docker compose logs -f app-1 app-2
```

특정 컨테이너 로그만 확인하려면:

```powershell
docker compose logs -f mysql
docker compose logs -f redis
```

## 종료 및 초기화

컨테이너만 종료합니다. MySQL 데이터는 볼륨에 유지됩니다.

```powershell
docker compose down
```

컨테이너를 다시 빌드해 실행합니다.

```powershell
docker compose up --build -d
```

**주의:** 아래 명령은 MySQL 데이터 볼륨까지 삭제하므로 데이터가 초기화됩니다.

```powershell
docker compose down -v
```

## 문제 해결

### 3306 포트 충돌

로컬 MySQL이 이미 3306 포트를 사용 중이면 Compose의 MySQL 컨테이너가 시작되지 않을 수 있습니다.

- 로컬 MySQL을 중지하거나
- `docker-compose.yml`의 MySQL 포트 매핑을 `"3307:3306"`으로 변경합니다.

포트 매핑을 3307로 변경해도 컨테이너 내부에서 사용하는 DB 주소 `mysql:3306`은 수정하지 않습니다.

### 컨테이너 상태 및 오류 확인

```powershell
docker compose ps
docker compose logs --tail=100 app-1 app-2 mysql redis
```

## 참고

- Compose 설정: [docker-compose.yml](../docker-compose.yml)
- 이미지 빌드 설정: [Dockerfile](../Dockerfile)
- Redis Pub/Sub은 메시지를 인스턴스 간 전달합니다. 현재 구성은 HTTP 세션 자체를 인스턴스 간 공유하도록 설정한 것은 아닙니다.
