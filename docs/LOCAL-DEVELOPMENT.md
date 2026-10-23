# 로컬 개발 환경

기능 브랜치에서 변경하고 로컬 테스트를 마친 뒤 Pull Request를 통해 main에 병합하는 흐름을 기준으로 합니다.

## 1. 사전 준비

- Docker Desktop 실행
- JDK 21
- Node.js/npm
- 저장소 루트에서 PowerShell 실행

로컬 MySQL은 Docker 컨테이너의 호스트 포트 3307을 사용합니다. Windows의 MySQL80 서비스를 중지하거나 포트를 바꿀 필요가 없습니다.

## 2. 시작

저장소 루트에서 실행합니다.

```powershell
git fetch origin
git switch <작업-브랜치>
git pull
.\start-local.ps1
```

첫 실행 시 스크립트가 다음 작업을 수행합니다.

1. Docker Desktop, npm, Gradle Wrapper 및 프론트엔드 파일 확인
2. 로컬 MySQL/Redis 컨테이너 시작 및 health check 대기
3. 프론트엔드 의존성이 없으면 package-lock.json 기준으로 설치
4. Spring Boot를 local 프로필로 백그라운드 실행
5. React/Vite 개발 서버 실행
6. 종료할 프로세스 정보를 저장

접속 주소:

- React UI: http://localhost:5173/ui/
- Spring Boot: http://localhost:8080
- MySQL: localhost:3307
- Redis: localhost:6379

로컬 실행은 운영용 .env를 요구하지 않습니다. DB 접속 설정은 docker-compose.local.yml 및 application-local.properties에서 관리합니다.

기본 포트 8080 또는 5173이 이미 사용 중이면 스크립트는 다른 프로세스를 강제로 종료하지 않고 오류를 표시합니다. 점유 프로세스를 확인한 뒤 처리하세요.

## 3. 종료

```powershell
.\stop-local.ps1
```

Spring Boot/React 프로세스와 로컬 MySQL/Redis 컨테이너를 종료합니다. 다른 앱이나 Windows 서비스는 건드리지 않으며 Docker volume의 DB 데이터는 유지됩니다.

개발 DB 데이터까지 삭제하려는 경우에만 실행합니다.

```powershell
docker compose -f docker-compose.local.yml down -v
```

## 4. 로그 및 상태 확인

```powershell
docker compose -f docker-compose.local.yml ps
Get-Content .local-backend.log -Tail 80
Get-Content .local-backend-error.log -Tail 80
Get-Content .local-frontend.log -Tail 80
Get-Content .local-frontend-error.log -Tail 80
```

## 5. 기능 개발 흐름

```text
main
  -> feature 브랜치 생성
  -> 변경사항 테스트
  -> .\start-local.ps1
  -> http://localhost:5173/ui/ 에서 기능 확인
  -> .\stop-local.ps1
  -> Pull Request 검토
  -> 테스트 통과 및 사용자 확인 후 main 병합
  -> GitHub Actions EC2 배포
```

운영용 docker-compose.yml은 로컬 개발용 포트나 비밀번호 때문에 변경하지 않습니다.
