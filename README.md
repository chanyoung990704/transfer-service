# Transfer & Payment Service (High-Availability)

본 프로젝트는 고가용성 분산 환경에서 안정적으로 동작하는 이체 및 결제 서비스를 목표로 개발되었습니다. 금융권에서 필수적인 멱등성 처리, 장애 내성, 성능 지표 확보, 그리고 데이터 정합성 검증 체계를 모두 갖추고 있습니다.

## 🚀 Key Features

### 1. 시스템 회복력 (Resilience)
- **Resilience4j 도입**: 외부 PG사 및 타 기관 API 장애가 서비스 전체로 전파되는 것을 차단하기 위해 **Circuit Breaker**를 적용했습니다.
- **Retry & Time Limiter**: 일시적인 네트워크 오류에 대해 자동 재시도 로직을 갖추고 있으며, 응답 지연 시 조기에 차단하여 자원 고갈을 방지합니다.

### 2. 통합 모니터링 (Observability)
- **Prometheus & Grafana**: 시스템 메트릭(TPS, Latency, Error Rate)을 실시간으로 수집하고 시각화합니다.
- **Distributed Tracing**: Micrometer Tracing과 Zipkin을 통해 분산 환경에서의 요청 흐름을 추적합니다.

### 3. 정량적 성능 지표 (Performance)
- **k6 Load Testing**: 시나리오 기반 부하 테스트를 통해 **RPS 2,000** 및 **P95 Latency 100ms** 이하의 목표 성능을 검증합니다.
- **NGINX Load Balancing**: 3대의 애플리케이션 인스턴스로 트래픽을 분산 처리합니다.

### 4. 데이터 정합성 검증 (Reconciliation)
- **Spring Batch**: 실시간 처리(Kafka 기반 Saga 패턴)의 실패 건을 식별하기 위한 **일일 정산 시스템**을 구축했습니다.
- **Audit Logging**: 모든 금융 거래의 이력을 별도 저장하여 추적성을 확보합니다.

### 5. 신뢰성 있는 거래 (Reliability)
- **Idempotency (멱등성)**: Redis 기반의 멱등성 체크를 통해 중복 결제 및 이체를 완벽히 방지합니다.
- **Event-Driven Saga Pattern**: Kafka를 활용하여 분산 환경에서도 최종적 일관성(Eventual Consistency)을 유지합니다.

## 🛠 Tech Stack
- **Language/Framework**: Java 17, Spring Boot 3.2.3, Spring Data JPA
- **Database**: MySQL 8.0, Redis (Idempotency, Distributed Lock)
- **Messaging**: Apache Kafka
- **Resilience**: Resilience4j (Circuit Breaker)
- **Batch**: Spring Batch
- **Monitoring**: Prometheus, Grafana, Zipkin
- **Test**: JUnit 5, Mockito, k6, Embedded Redis, Embedded Kafka

## 🏃 How to Run
```bash
# Infrastructure 기동 (MySQL, Redis, Kafka, Zipkin, Prometheus, Grafana, NGINX)
docker-compose up -d

# Application 실행
./gradlew bootRun
```

## 📊 Performance Report
상세한 성능 측정 결과는 [PERFORMANCE.MD](./PERFORMANCE.MD)를 참고해 주세요.
