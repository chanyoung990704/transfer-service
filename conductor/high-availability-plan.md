# 고가용성 및 안정성 강화 상세 구현 계획

본 계획은 금융권 서버 개발자 JD의 핵심 요구사항인 '대용량 트래픽 처리', '시스템 안정성', '데이터 정합성'을 증명하기 위한 기술적 고도화 전략입니다.

## 1. 배경 및 목적
현재 `transfer-service`는 기본적인 이체/결제 및 멱등성 로직을 갖추고 있으나, 실제 운영 환경에서 발생할 수 있는 외부 장애 전파 방지(Circuit Breaker), 정량적 성능 지표(RPS/Latency), 실시간 데이터 정합성 검증(Reconciliation) 부분이 보완되어야 합니다. 이를 통해 기술적 완성도를 높여 합격 가능성을 극대화합니다.

## 2. 단계별 구현 상세

### Phase 1: Resilience4j를 통한 시스템 회복력 강화
- **Objective:** 외부 PG사 또는 타 기관 API 장애 시 시스템 전체 마비 방지
- **Key Files:** `PaymentService.java`, `TransferService.java`, `application.yml`
- **Implementation Steps:**
    1. `resilience4j-spring-boot3` 의존성 추가
    2. `CircuitBreaker`, `TimeLimiter`, `Retry` 설정 정의
    3. 외부 API 호출부(PaymentConfirm 등)에 `@CircuitBreaker` 적용
    4. 장애 상황 대응을 위한 Fallback 메서드 구현
- **Verification:** Mockito 또는 WireMock을 이용한 장애 시뮬레이션 테스트 작성

### Phase 2: Prometheus & Grafana 통합 모니터링
- **Objective:** 시스템 메트릭 시각화를 통한 운영 가시성 확보
- **Key Files:** `docker-compose.yml`, `application.yml`, Grafana Dashboard JSON
- **Implementation Steps:**
    1. Actuator 메트릭 노출 설정 (`/actuator/prometheus`)
    2. Prometheus 설정 파일(`prometheus.yml`) 작성 및 컨테이너 추가
    3. Grafana 컨테이너 추가 및 Prometheus 데이터 소스 연결
    4. 주요 지표(TPS, Latency, Error Rate) 대시보드 구성
- **Verification:** 서비스 기동 후 Grafana 대시보드에서 실시간 메트릭 확인

### Phase 3: k6 부하 테스트 및 성능 최적화
- **Objective:** 시스템의 한계 RPS 측정 및 병목 지점 개선
- **Key Files:** `scripts/k6-load-test.js`, `README.md`
- **Implementation Steps:**
    1. k6 설치 및 이체/결제 시나리오 스크립트 작성
    2. 단계별 부하 테스트(Smoke, Load, Stress) 수행
    3. 성능 측정 결과(RPS, P95 Latency) 기록
    4. 필요 시 DB 인덱스 추가 또는 Kafka 파티션 조절 등 최적화 수행
- **Verification:** 최적화 전/후의 RPS 변화 수치 비교 보고서 작성

### Phase 4: Spring Batch 기반 정산(Reconciliation) 시스템
- **Objective:** 데이터 누락 및 불일치를 찾아내는 2차 검증 체계 구축
- **Key Files:** `SettlementJobConfig.java`, `PaymentRepository.java`
- **Implementation Steps:**
    1. Spring Batch 인프라 설정 및 JobRepository 구성
    2. '일일 결제 데이터 정산' Job 설계 (Read: Payment DB, Processor: 검증 Logic, Write: Result Table)
    3. 배치 수행 이력 및 실패 건 관리 UI/Log 구현
- **Verification:** 수동으로 정합성을 깨뜨린 데이터를 배치로 식별해내는지 테스트

## 3. 예상 결과물
- **기술 블로그/포트폴리오 업데이트:** "RPS 2,000 달성 및 Resilience 패턴을 통한 장애 복구 사례"
- **코드 퀄리티:** 프로덕션 수준의 안정성을 갖춘 금융 백엔드 시스템
- **정량적 수치:** 테스트 전/후 성능 향상 지표 (예: Latency 30% 개선 등)

## 4. 향후 일정
- Week 1: Phase 1 & 2 완료
- Week 2: Phase 3 & 4 완료 및 최종 검증
