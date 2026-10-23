# 저장소 구조

프로젝트 루트에서 애플리케이션 코드, 실행 설정, 테스트 도구, 문서를 목적별로 분리합니다. 파일을 옮기기 전에 Docker build context, Vite 출력 경로, Spring 정적 리소스 경로, GitHub Actions 참조를 함께 확인합니다.

```text
RealTimeChat/
├── .github/workflows/       # PR CI, EC2 자동 배포, 수동 Windows Compose workflow
├── deploy/nginx/            # Nginx reverse proxy 설정
├── docs/                    # 실행 가이드, 성능 분석, 개선 이력
│   └── assets/              # 문서용 이미지·동영상
├── frontend/                # React + Vite UI
│   └── src/                 # 화면과 스타일
├── gradle/wrapper/          # Gradle Wrapper
├── k6/                      # 실행 가능한 채팅 부하 테스트
├── monitoring/              # Prometheus/Grafana 설정과 대시보드
├── src/main/java/com/realtimechat/
│   ├── auth/                # 세션 인증과 사용자 프로필
│   ├── chat/                # 채팅방, 메시지, 읽음 상태, Redis 중계
│   └── config/              # 공통 WebSocket 및 접근 제어 설정
├── src/main/resources/      # Spring 설정과 정적 리소스
├── src/test/java/           # 백엔드 테스트
├── Dockerfile
├── docker-compose.yml       # 운영/EC2 서비스와 선택형 모니터링
├── docker-compose.local.yml # 로컬 MySQL·Redis만 실행
├── start-local.ps1          # 로컬 개발 환경 시작
└── stop-local.ps1           # 로컬 개발 환경 종료
```

## 주요 파일의 책임

| 목적 | 위치 |
|---|---|
| 공통·로컬 Spring 설정 | `src/main/resources/application*.properties` |
| 프론트엔드 의존성·빌드 | `frontend/package.json`, `frontend/package-lock.json`, `build.gradle` |
| STOMP 및 채팅방 접근 제어 | `src/main/java/com/realtimechat/config/` |
| 인증·채팅 도메인 | `src/main/java/com/realtimechat/auth/`, `src/main/java/com/realtimechat/chat/` |
| 로컬 실행 | `docker-compose.local.yml`, `start-local.ps1`, `stop-local.ps1` |
| 운영 Compose/Nginx | `docker-compose.yml`, `deploy/nginx/default.conf` |
| PR 테스트 | `.github/workflows/ci.yml` |
| EC2 자동 배포 | `.github/workflows/deploy-ec2.yml` |
| 수동 Windows Compose 작업 | `.github/workflows/deploy.yml` |
| 모니터링 | `monitoring/`, `docs/MONITORING.md` |
| 성능 테스트 | `k6/`, `docs/K6.md`, `docs/EC2-PERFORMANCE-ANALYSIS.md` |
| 프로젝트 개선 기록 | `docs/PROJECT-IMPROVEMENTS.md`, `docs/PROJECT-REPORT.md` |

## k6 스크립트 구분

- `chat-load.js`: 테스트 사용자를 만들고 두 앱 인스턴스에 번갈아 접속하는 기본 교차 인스턴스 부하 테스트
- `two-account-chat.js`: 기존 두 계정이 서로 메시지를 주고받는 2 VU 순서·지연 검증
- `multi-vu-chat-load.js`: 여러 VU에서 계정별 스트림을 구분해 부하 및 순서 지표를 관측

스크립트들은 시나리오가 서로 다르므로 하나로 합치지 않습니다. 테스트 계정과 채팅방을 DB에 만들 수 있으므로 운영 DB에서는 실행하지 마세요.

## 정리 원칙

- `frontend/node_modules/`, Vite 캐시, 빌드 산출물, 로그, `.env`는 커밋하지 않습니다. 예외적으로 `.env.example`은 추적합니다.
- 문서용 미디어는 루트가 아닌 `docs/assets/`에 둡니다.
- `src/main/resources/static/index.html`, `login.html`, `chat.html`은 각각 React UI 경로로 보내는 호환용 리다이렉트입니다. 현재 화면 구현과 중복되는 페이지가 아니므로 별도 경로 호환성을 확인하기 전에는 삭제하지 않습니다.
- 기본 Spring 컨텍스트 스모크 테스트는 `src/test/java/com/realtimechat/RealTimeChatApplicationTests.java`에 둡니다.
- Gradle Wrapper 파일은 실행 환경에 필요하므로 삭제하거나 수동 편집하지 않습니다.
- 로컬과 운영 Compose는 포트·비밀번호·실행 서비스가 다르므로 한 파일로 합치지 않습니다.
