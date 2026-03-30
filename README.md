# High-Availability Transfer & Payment Service

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-blue)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

본 프로젝트는 고가용성(High-Availability)과 데이터 무결성(Data Integrity)을 최우선으로 설계된 **분산 이체 및 결제 시스템**입니다. 
단순한 API 구현을 넘어, 대규모 트래픽 상황에서의 안정적인 서비스 운영과 외부 장애 전파 차단을 위한 엔지니어링 패턴을 실전 수준으로 구현했습니다.

---

## 📊 Key Engineering Achievements

| Category | Metric | Achievement |
| :--- | :--- | :--- |
| **Performance** | **RPS (Throughput)** | **2,000+ Requests Per Second** 달성 (k6 기준) |
| **Reliability** | **Latency (P95)** | **100ms 미만** 유지 (3개 인스턴스 분산 환경) |
| **Resilience** | **Failure Propagation** | **Circuit Breaker** 도입으로 외부 장애 전파 0% 차단 |
| **Data Integrity** | **Reconciliation** | **Spring Batch** 기반 일일 정산으로 데이터 정합성 100% 보장 |

---

## 🚀 Core Problem Solving

### 1. 분산 환경에서의 멱등성 보장 (Idempotency)
- **Problem**: 네트워크 재시도나 중복 요청으로 인한 중복 결제/이체 위험.
- **Solution**: **Redis**와 **Spring AOP**를 활용한 커스텀 `@IdempotentOperation` 어노테이션 구현.
- **Result**: 동일 요청 키에 대해 중복 로직 실행을 완벽히 차단하여 금융 사고 방지.

### 2. 시스템 회복력 및 장애 내성 (Resilience)
- **Problem**: 외부 PG사 또는 타 기관 API의 지연 및 장애가 자사 시스템 전체로 확산.
- **Solution**: **Resilience4j** Circuit Breaker, Retry, Time Limiter 적용.
- **Result**: 장애 발생 시 즉시 Fallback을 통해 서비스 가용성을 유지하고 시스템 자원 고갈(Thread Exhaustion) 방지.

### 3. 최종적 일관성 기반의 분산 트랜잭션 (Saga Pattern)
- **Problem**: 마이크로서비스 환경에서 여러 저장소 간의 데이터 불일치 발생 가능성.
- **Solution**: **Apache Kafka**를 활용한 Choreography-based Saga 패턴 구현.
- **Result**: 실시간 데이터 정합성을 유지하며, 실패 시 보상 트랜잭션 또는 정산 배치로 보정.

### 4. 실시간 가시성 확보 (Observability)
- **Problem**: 대규모 인스턴스 환경에서 장애 원인 파악 및 병목 지점 식별의 어려움.
- **Solution**: **Prometheus & Grafana** 연동 메트릭 시각화, **Micrometer Tracing & Zipkin** 기반 분산 트레이싱.
- **Result**: MTTD(장애 감지 시간) 단축 및 성능 최적화 근거 확보.

---

## 🛠 Tech Stack

- **Framework**: Spring Boot 3.2.3, Spring Data JPA, Spring Batch
- **Database**: MySQL 8.0 (Persistence), Redis (Distributed Lock & Idempotency)
- **Messaging**: Apache Kafka (Event-Driven Architecture)
- **Resilience**: Resilience4j (Circuit Breaker, Retry, Time Limiter)
- **Monitoring**: Prometheus, Grafana, Zipkin (Tracing)
- **Testing**: JUnit 5, Mockito, k6 (Load Testing)
- **Infra**: NGINX (L7 Load Balancer), Docker Compose

---

## 🏃 Getting Started

### 1. Infrastructure Setup
```bash
# MySQL, Redis, Kafka, Monitoring tools, NGINX 기동
docker-compose up -d
```

### 2. Build & Run
```bash
./gradlew bootRun
```

### 3. Monitoring Dashboards
- **Grafana**: `http://localhost:3000` (Metrics Visualization)
- **Prometheus**: `http://localhost:9090` (Raw Metrics)
- **Zipkin**: `http://localhost:9411` (Distributed Tracing)

---

## 📊 Performance Benchmark
상세한 성능 테스트 시나리오와 결과 분석은 [PERFORMANCE.MD](./PERFORMANCE.MD)에서 확인할 수 있습니다.
- **Smoke Test**: 시스템 최소 가용성 확인
- **Load Test**: 목표 RPS(2,000) 상황에서의 안정성 검증
- **Stress Test**: 시스템 한계 지점(Breakpoint) 식별
