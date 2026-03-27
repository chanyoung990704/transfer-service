# Transfer Service Project Status & Version Report

이 문서는 고가용성 분산 환경을 고려하여 설계된 **이체 및 결제 서비스(Transfer & Payment Service)**의 아키텍처와 현재 구현 상태를 상세히 설명합니다.

## 🚀 프로젝트 개요
본 프로젝트는 단순한 이체 기능을 넘어, 분산 시스템에서 발생할 수 있는 데이터 정합성 문제와 확장성 문제를 해결하기 위해 다양한 마이크로서비스 패턴을 적용한 레퍼런스 프로젝트입니다. 최근 **결제 시스템(Toss Payments 스타일)** 아키텍처가 추가되었습니다.

---

## 🛠 기술 스택
...

### 3. 결제 상태 머신 (Payment State Machine)
결제의 신뢰성을 위해 엄격한 상태 전이 규칙을 적용한 상태 머신을 도입했습니다.
- **상태 종류**: `READY` -> `IN_PROGRESS` -> `DONE` / `CANCELED` / `ABORTED` / `PARTIAL_CANCELED`.
- **특징**: 잘못된 상태 전이(예: 취소된 결제를 완료로 변경)를 도메인 계층에서 원천 차단합니다.

### 4. 결제 위변조 검증 및 3-Party 승인
- **위변조 검증**: 승인 요청(`confirm`) 시, 클라이언트가 보낸 금액이 내부 DB에 저장된 주문 금액과 일치하는지 대조하여 위변조를 방지합니다.
- **3-Party 승인**: 가맹점(우리 서버) -> PG사(승인 대행) -> 카드사로 이어지는 승인 흐름을 시뮬레이션하여 구현했습니다.
- **Transactional Outbox**: 결제 승인 완료 시 `payment_events` 테이블에 이벤트를 저장하여 타 시스템(배송, 알림 등)으로의 전파 정합성을 보장합니다.

---

## 📂 주요 소스 코드 구조

| 패키지 / 파일 | 설명 |
|---|---|
| `api.TransferController` | 이체 요청 접수 및 멱등성 처리 어노테이션 적용 |
| `api.PaymentController` | 결제 생성 및 승인 API (멱등성 적용) |
| `domain.transfer.TransferService` | Saga 각 단계(출금, 입금, 환불)의 로컬 트랜잭션 처리 |
| `domain.payment.Payment` | 결제 도메인 모델 및 상태 전이 로직 (READY, DONE 등) |
| `domain.payment.PaymentService` | 결제 승인 프로세스 (금액 검증 + PG 연동 시뮬레이션) |
| `domain.payment.PaymentEventEntity` | 결제용 Transactional Outbox 엔티티 |
| `domain.event.TransferEventRelay` | 아웃박스 패턴의 핵심인 메시지 발행기 |
| `idempotency.IdempotencyAspect` | Redis 기반 멱등성 보장 AOP |

---

## ✅ 현재 상태 및 검증 내역
- **이체 시스템**: Saga 패턴 및 Outbox 패턴 통합 테스트 완료.
- **결제 시스템**: **[진행 중]** 결제 승인 흐름 및 위변조 검증 구현 완료. (Phase 2)
- **인프라**: NGINX 로드 밸런싱 및 멀티 인스턴스 구성 완료.

**최종 업데이트 일자**: 2026-03-27 (결제 승인 흐름 추가)
