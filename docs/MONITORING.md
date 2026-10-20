# Prometheus + Grafana 모니터링

## 구성

- Spring Boot Actuator: JVM, 프로세스, HTTP 요청 등 애플리케이션 메트릭 노출
- Prometheus: 5초 간격으로 app-1과 app-2의 메트릭 수집
- Grafana: Prometheus 메트릭 조회 및 대시보드 시각화
- 관리 엔드포인트는 컨테이너 내부 9090 포트로 분리하며 호스트 포트에 공개하지 않습니다.

## 실행

Docker Desktop 실행 후 프로젝트 루트에서:

```powershell
docker compose up --build -d
docker compose ps
```

접속 주소:

- Grafana: http://localhost:3000 (기본 계정 `admin` / `admin`, 최초 로그인 후 변경 권장)
- Prometheus: http://localhost:9091

Grafana 데이터 소스는 provisioning으로 자동 등록됩니다.

## 수집 상태 확인

Prometheus의 **Status → Targets**에서 `spring-boot-app-1`, `spring-boot-app-2` 상태가 모두 `UP`인지 확인합니다.

Prometheus의 Graph 페이지에서 다음 쿼리를 실행할 수 있습니다.

- HTTP 요청 처리량: `rate(http_server_requests_seconds_count[1m])`
- HTTP 요청 p95 응답시간: `histogram_quantile(0.95, sum by (le, instance_name) (rate(http_server_requests_seconds_bucket[5m])))`
- JVM 메모리 사용량: `sum by (instance_name) (jvm_memory_used_bytes)`
- CPU 사용량: `process_cpu_usage`
- 가동 시간: `process_uptime_seconds`

메트릭은 요청이 발생한 후에 표시될 수 있습니다. 요청 지표가 0이거나 비어 있으면 먼저 웹 UI를 이용하고 Prometheus Targets가 정상인지 확인하세요.

## 종료 및 데이터 보존

```powershell
docker compose stop
docker compose start
```

컨테이너를 제거하고 다시 만들려면 `docker compose down`을 사용합니다. Prometheus와 Grafana 데이터는 named volume에 보존됩니다. **`docker compose down -v`는 모니터링 데이터와 MySQL 데이터도 삭제하므로 주의하세요.**

## 보안 주의

기본 Grafana 계정은 로컬 개발용입니다. 외부에 노출되는 서버에서는 반드시 `GRAFANA_ADMIN_USER`와 `GRAFANA_ADMIN_PASSWORD`를 강력한 값으로 설정하고, 포트 접근을 방화벽으로 제한하세요. Actuator 관리 포트는 호스트에 직접 publish하지 않습니다.
