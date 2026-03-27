# Transfer Service Project Status & Version Report

이 문서는 고가용성 분산 환경을 고려하여 설계된 **이체 및 결제 서비스(Transfer & Payment Service)**의 아키텍처와 현재 구현 상태를 상세히 설명합니다.

## 🚀 프로젝트 개요
본 프로젝트는 단순한 이체 기능을 넘어, 분산 시스템에서 발생할 수 있는 데이터 정합성 문제와 확장성 문제를 해결하기 위해 다양한 마이크로서비스 패턴을 적용한 레퍼런스 프로젝트입니다.

---

## 🛠 기술 스택
- **Framework**: Spring Boot 3.2.3, Java 17
- **Database**: MySQL 8.0 (Pessimistic Lock 적용)
- **Messaging**: Apache Kafka (Exactly-once Semantics)
- **Cache & Lock**: Redis (Idempotency, ZSet, Pub/Sub, ShedLock 준비)
- **Infrastructure**: NGINX (Load Balancer), Docker Compose, **Zipkin (Distributed Tracing)**
- **Monitoring**: Spring Boot Actuator, **Micrometer Tracing**

---

## 🏗 핵심 아키텍처 패턴

### 5. 관측 가능성 (Observability) - 분산 추적
분산된 인스턴스와 비동기 메시징(Kafka) 환경에서 요청의 전체 경로를 추적합니다.
- **Micrometer Tracing**: Spring Boot 3의 표준 추적 라이브러리를 사용하여 모든 API 요청과 Kafka 메시지에 `traceId`를 부여합니다.
- **Zipkin 통합**: 수집된 추적 데이터를 Zipkin 서버로 전송하여 가각의 Span(작업 단위)을 시각화합니다.
- **로그 통합**: 로그 패턴에 `traceId`와 `spanId`를 포함하여, 로그 시스템만으로도 특정 요청의 흐름을 추적할 수 있습니다.

---

## ✅ 현재 상태 및 검증 내역
- **이체 및 결제 시스템**: Saga 패턴, Outbox 패턴, 위변조 검증 기능 통합 완료.
- **멀티 인스턴스**: NGINX 로드 밸런싱 및 HA 구성 완료.
- **관측 가능성**: **[완료]** Micrometer Tracing 및 Zipkin 리포터 설정 완료. (Phase 1)

**최종 업데이트 일자**: 2026-03-27 (분산 추적 시스템 추가)
