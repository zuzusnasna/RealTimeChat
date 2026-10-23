# Prometheus + Grafana 모니터링

## 구성

- Spring Boot Actuator: JVM, 프로세스, HTTP 요청 메트릭 노출
- Prometheus: 5초 간격으로 app-1과 app-2의 메트릭 수집
- Grafana: Prometheus 메트릭 조회 및 대시보드 시각화
- 관리 엔드포인트는 컨테이너 내부 9090 포트로 분리하며 호스트에 직접 공개하지 않습니다.
- EC2 메모리 사용량을 줄이기 위해 두 모니터링 서비스는 기본 Compose 실행에서 제외되어 있습니다.

## 실행

Docker Desktop 또는 EC2 프로젝트 디렉터리에서 실행합니다.

```powershell
docker compose --profile monitoring up -d
docker compose --profile monitoring ps
```

EC2처럼 메모리가 작은 환경에서는 조사할 때만 켜고 확인이 끝나면 중지하세요.

```powershell
docker compose stop grafana prometheus
```

접속 주소:

- Grafana: http://localhost:3000 (호스트의 localhost에만 바인딩)
- Prometheus: http://localhost:9091

Grafana 계정은 `.env`의 `GRAFANA_ADMIN_USER` 및 `GRAFANA_ADMIN_PASSWORD`로 설정합니다. 해당 비밀번호는 필수이며 실제 운영 환경에서는 강력한 값으로 지정하세요.

## 수집 상태 확인

Prometheus의 **Status → Targets**에서 `spring-boot-app-1`, `spring-boot-app-2` 상태가 모두 `UP`인지 확인합니다.

Prometheus Graph에서 사용할 수 있는 쿼리 예시:

- HTTP 요청 처리량: `rate(http_server_requests_seconds_count[1m])`
- HTTP 요청 p95 응답시간: `histogram_quantile(0.95, sum by (le, instance_name) (rate(http_server_requests_seconds_bucket[5m])))`
- JVM 메모리 사용량: `sum by (instance_name) (jvm_memory_used_bytes)`
- CPU 사용량: `process_cpu_usage`
- 가동 시간: `process_uptime_seconds`

메트릭은 요청이 발생한 후에 표시될 수 있습니다. 요청 지표가 비어 있으면 웹 UI를 이용한 뒤 Prometheus Targets 상태부터 확인하세요.

## 데이터 보존 및 주의

`docker compose stop`은 컨테이너를 중지하고, `docker compose down`은 컨테이너를 제거하지만 named volume은 유지합니다. **`docker compose down -v`는 모니터링 데이터와 MySQL 데이터까지 삭제하므로 주의하세요.**

Actuator 관리 포트는 호스트에 직접 publish하지 않습니다. 모니터링 포트를 외부에 공개하지 말고 접근 범위를 제한하세요.
