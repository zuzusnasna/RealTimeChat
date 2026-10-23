# 저장소 구조

프로젝트 루트에서 백엔드, 프론트엔드, 배포 설정, 모니터링, 테스트 도구를 분리해 관리합니다.

```text
RealTimeChat/
├── .github/workflows/       # CI, Windows runner 배포, EC2 배포 워크플로
├── deploy/nginx/            # Nginx reverse proxy 설정
├── docs/                    # 실행 가이드, 운영 분석, 개선 이력
│   └── assets/              # 문서에서 참조하는 이미지·동영상 등
├── frontend/                # React + Vite UI
│   └── src/                 # 화면 진입점과 스타일
├── gradle/wrapper/          # Gradle Wrapper
├── k6/                      # WebSocket/채팅 부하 테스트 스크립트
├── monitoring/              # Prometheus와 Grafana 설정·대시보드
├── src/main/java/           # Spring Boot 인증·채팅·WebSocket 코드
├── src/main/resources/      # Spring 설정과 정적 리소스
├── src/test/java/           # 백엔드 테스트
├── Dockerfile
├── docker-compose.yml       # 기본/운영 Compose 구성
├── docker-compose.local.yml # 로컬 MySQL·Redis 전용 구성
├── start-local.ps1          # 로컬 개발 환경 시작
└── stop-local.ps1           # 로컬 개발 환경 종료
```

## 정리 기준

- `frontend/.vite/`는 Vite가 생성하는 캐시이므로 저장소에 커밋하지 않습니다.
- `k6/`에는 실제 실행 가능한 테스트 스크립트만 둡니다. 임시 파일이나 빈 파일은 저장소에서 제거합니다.
- 성능 화면 녹화 등 문서용 미디어는 루트에 두지 않고 `docs/assets/`에 보관합니다.
- 인증 코드는 `auth/`, 채팅 도메인은 `chat/`, 공통 WebSocket 설정은 `config/` 아래에서 관리합니다.
- 기존 `src/main/resources/static/*.html`은 실제 사용 여부를 확인하지 않고 삭제하지 않습니다.
- 패키지나 실행 설정을 이동할 때는 Java package 경로, Docker build context, Vite 출력 경로, 워크플로의 파일 경로가 함께 바뀌는지 확인합니다.

## 주요 파일 위치

| 목적 | 위치 |
|---|---|
| 앱 설정 | `src/main/resources/application*.properties` |
| STOMP 및 접근 제어 | `src/main/java/com/realtimechat/config/` |
| 채팅 저장·전달 | `src/main/java/com/realtimechat/chat/` |
| 인증 | `src/main/java/com/realtimechat/auth/` |
| 프론트엔드 | `frontend/src/` |
| 로컬 개발 | `docker-compose.local.yml`, `start-local.ps1`, `stop-local.ps1` |
| 운영 Compose/Nginx | `docker-compose.yml`, `deploy/nginx/default.conf` |
| 부하 테스트 | `k6/`, `docs/K6.md` |
| EC2 성능 기록 | `docs/EC2-PERFORMANCE-ANALYSIS.md` |
| 개선 이력 | `docs/PROJECT-IMPROVEMENTS.md` |
