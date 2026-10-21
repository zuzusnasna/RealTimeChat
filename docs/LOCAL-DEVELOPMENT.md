# 로컬 개발 환경

기능 브랜치에서 개발하고 로컬 테스트한 뒤 \`main\`에 병합하는 것을 기준으로 합니다.

## 1. 사전 준비

- Docker Desktop 실행
- JDK 21 설치
- Node.js/npm 설치
- 저장소를 \`feature/*\` 브랜치로 checkout

Windows의 \`MySQL80\` 서비스를 켜거나 끌 필요가 없습니다. 로컬 MySQL은 host port 3307을 사용합니다.

## 2. 한 번에 시작하기

프로젝트 루트(\`C:\dev\RealTimeChat\`)에서 PowerShell을 열고:

\`\`\`powershell
git fetch origin
git checkout feature/local-dev-environment
git pull
.\start-local.ps1
\`\`\`

시작 스크립트가 자동으로:
1. Docker Desktop이 실행 중인지 확인
2. 로컬 MySQL과 Redis 컨테이너 시작 및 healthy 상태 대기
3. 최초 실행이면 frontend 의존성 설치
4. Spring Boot를 \`local\` 프로필로 백그라운드 실행
5. React/Vite 개발 서버를 백그라운드 실행
6. 종료할 때 쓸 프로세스 ID 저장

브라우저에서 \`http://localhost:5173/ui/\`로 접속합니다. React와 Spring Boot 로그는 프로젝트 루트의 \`.local-*.log\` 파일에서 확인할 수 있습니다.

기본 포트 8080 또는 5173을 이미 다른 프로세스가 사용 중이면 스크립트는 중복 실행하지 않고 오류를 표시합니다. 기존 프로세스를 확인/종료한 뒤 다시 실행하세요.

## 3. 한 번에 종료하기

프로젝트 루트에서:

\`\`\`powershell
.\stop-local.ps1
\`\`\`

스크립트가 이 시작 스크립트가 띄운 Spring Boot/React 프로세스 트리와 로컬 MySQL/Redis 컨테이너를 종료합니다. 다른 앱이나 Windows 서비스는 건드리지 않습니다. Docker volume의 DB 데이터는 유지됩니다.

데이터까지 완전히 지우려는 경우에만 별도로 실행하세요:

\`\`\`powershell
docker compose -f docker-compose.local.yml down -v
\`\`\`

## 4. 문제 확인

\`\`\`powershell
docker compose -f docker-compose.local.yml ps
Get-Content .local-backend.log -Tail 80
Get-Content .local-backend-error.log -Tail 80
Get-Content .local-frontend.log -Tail 80
Get-Content .local-frontend-error.log -Tail 80
\`\`\`

Docker MySQL/Redis 설정은 \`docker-compose.local.yml\`, Spring 설정은 \`src/main/resources/application-local.properties\`, React 개발 서버와 API/WebSocket 프록시는 \`frontend/vite.config.js\`에서 관리합니다.

## 5. 기능 개발과 배포 흐름

\`\`\`text
main
  -> feature/기능명
  -> git pull
  -> .\start-local.ps1
  -> http://localhost:5173/ui/ 에서 기능 테스트
  -> .\stop-local.ps1
  -> 테스트 통과 후 feature 브랜치를 main에 병합
  -> GitHub Actions가 EC2 재빌드/배포
\`\`\`

운영용 \`docker-compose.yml\`은 이 로컬 개발 설정 때문에 수정하지 않습니다.
