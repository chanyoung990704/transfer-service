# Transfer Service Project Status & Version Report

이 문서는 고가용성 분산 환경을 고려하여 설계된 **이체 및 결제 서비스(Transfer & Payment Service)**의 아키텍처와 현재 구현 상태를 상세히 설명합니다.

## 🚀 프로젝트 개요
본 프로젝트는 단순한 이체 기능을 넘어, 분산 시스템에서 발생할 수 있는 데이터 정합성 문제와 확장성 문제를 해결하기 위해 다양한 마이크로서비스 패턴을 적용한 레퍼런스 프로젝트입니다. 최근 **결제 시스템(Toss Payments 스타일)** 아키텍처가 추가되었습니다.

---

## 🛠 기술 스택
- **Framework**: Spring Boot 3.2.3, Java 17
- **Database**: MySQL 8.0 (Pessimistic Lock 적용)
- **Messaging**: Apache Kafka (Exactly-once Semantics)
- **Cache & Lock**: Redis (Idempotency, ZSet, Pub/Sub)
- **Infrastructure**: NGINX (Load Balancer), Docker Compose
- **Monitoring**: Spring Boot Actuator

---

## 🏗 핵심 아키텍처 패턴

### 1. Saga 패턴 (Choreography 기반)
분산 트랜잭션의 최종 정합성을 보장하기 위해 단일 트랜잭션을 단계별 로컬 트랜잭션으로 분리했습니다.
- **1단계 (출금)**: `TransferService.transfer()` 호출 시 출금 계좌에서 금액 차감 후 `WITHDRAWN` 이벤트 저장.
- **2단계 (입금)**: `TransferEventConsumer`가 이벤트를 구독하여 입금 계좌에 금액 입금 시도.
- **보상 트랜잭션**: 입금 실패 시 `refund()` 로직을 통해 출금된 금액을 원복(환불) 처리.

### 2. Transactional Outbox 패턴
DB 업데이트와 메시지 발행의 원자성을 보장합니다.
- 서비스는 Kafka에 직접 메시지를 던지지 않고, `transfer_events` 테이블(Outbox)에 상태를 저장합니다.
- `TransferEventRelay`(스케줄러)가 주기적으로 미발행 이벤트를 읽어 Kafka로 전송합니다.

### 3. 결제 상태 머신 (Payment State Machine)
결제의 신뢰성을 위해 엄격한 상태 전이 규칙을 적용한 상태 머신을 도입했습니다.
- **상태 종류**: `READY` -> `IN_PROGRESS` -> `DONE` / `CANCELED` / `ABORTED` / `PARTIAL_CANCELED`.
- **특징**: 잘못된 상태 전이(예: 취소된 결제를 완료로 변경)를 도메인 계층에서 원천 차단합니다.

### 4. 결제 위변조 검증 및 3-Party 승인
- **위변조 검증**: 외부 PG사 승인 요청 전, 클라이언트가 보낸 금액이 내부 주문 시스템의 금액과 일치하는지 검증합니다.
- **3-Party 구조**: 가맹점(우리 서버) -> PG사(Toss 등) -> 카드사로 이어지는 승인 흐름을 시뮬레이션합니다.

---

## 📂 주요 소스 코드 구조

| 패키지 / 파일 | 설명 |
|---|---|
| `api.TransferController` | 이체 요청 접수 및 멱등성 처리 어노테이션 적용 |
| `domain.transfer.TransferService` | Saga 각 단계(출금, 입금, 환불)의 로컬 트랜잭션 처리 |
| `domain.payment.Payment` | 결제 도메인 모델 및 상태 전이 로직 (READY, DONE 등) |
| `domain.payment.PaymentStatus` | 결제 상태 머신 Enum 정의 |
| `domain.event.TransferEventRelay` | 아웃박스 패턴의 핵심인 메시지 발행기 |
| `idempotency.IdempotencyAspect` | Redis 기반 멱등성 보장 AOP |

---

## ✅ 현재 상태 및 검증 내역
- **이체 시스템**: Saga 패턴 및 Outbox 패턴 통합 테스트 완료.
- **결제 시스템**: **[진행 중]** 도메인 모델 및 상태 머신 구현 완료. (Phase 1)
- **인프라**: NGINX 로드 밸런싱 및 멀티 인스턴스 구성 완료.

**최종 업데이트 일자**: 2026-03-27 (결제 시스템 도메인 추가)
