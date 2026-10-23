# Docker Compose 사용법

저장소에는 목적이 다른 Compose 설정이 있습니다.

- docker-compose.yml: 운영/EC2용 Nginx, app-1/app-2, MySQL, Redis 및 선택형 모니터링
- docker-compose.local.yml: 로컬 개발용 MySQL/Redis만 실행. Spring Boot와 React는 PowerShell 스크립트가 로컬 프로세스로 실행

## 운영 Compose 구성 확인

운영 Compose는 .env의 DB_PASSWORD가 필요합니다. 모니터링을 실행할 때는 GRAFANA_ADMIN_PASSWORD도 설정해야 합니다.

```powershell
docker compose config --services
docker compose up --build -d
docker compose ps
```

기본 실행은 모니터링 프로필을 시작하지 않습니다. EC2에서는 불필요한 자원 사용을 줄이기 위해 기본 서비스를 유지하고, 필요할 때만 모니터링을 실행하세요.

## 접속

- 운영/Compose UI: http://localhost/ui/ (Nginx 경유)
- 로컬 개발 UI: http://localhost:5173/ui/ (start-local.ps1 실행 후)

운영 Compose는 app-1/app-2의 8080 포트를 호스트에 직접 publish하지 않습니다. 인스턴스 간 메시지 전달을 시험하려면 k6 가이드의 테스트 대상 주소 조건을 확인하세요.

## 로그 및 상태

```powershell
docker compose ps
docker compose logs -f app-1 app-2
docker compose logs --tail=100 mysql redis
```

## 종료 및 데이터 보존

컨테이너를 중지하고 제거해도 named volume은 유지됩니다.

```powershell
docker compose down
```

**주의:** 다음 명령은 MySQL과 모니터링 데이터가 들어 있는 volume까지 삭제합니다. 개발 데이터 초기화가 의도된 경우에만 사용하세요.

```powershell
docker compose down -v
```

## 로컬 DB/Redis만 실행

로컬 개발에서는 start-local.ps1이 아래 설정을 사용합니다.

```powershell
docker compose -f docker-compose.local.yml up -d
docker compose -f docker-compose.local.yml ps
```

- MySQL 호스트 포트: 3307
- Redis 호스트 포트: 6379
- 데이터 볼륨은 stop-local.ps1 실행 후에도 유지됩니다.

## 문제 해결

### 포트 충돌

- 운영 Compose의 공개 진입점은 Nginx의 80 포트입니다.
- 로컬 개발 API는 8080, Vite는 5173을 사용합니다.
- 로컬 MySQL은 3307을 사용해 Windows MySQL의 기본 포트 3306과 분리됩니다.

점유 중인 프로세스를 확인하지 않은 채 다른 서비스를 강제로 종료하지 마세요.

## 참고

- [로컬 개발 가이드](LOCAL-DEVELOPMENT.md)
- [모니터링 가이드](MONITORING.md)
- [k6 부하 테스트](K6.md)
