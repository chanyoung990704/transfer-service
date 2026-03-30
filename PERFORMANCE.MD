# Performance Benchmark (k6)

## 1. 개요
시스템의 성능 한계를 파악하고, 최적화 전/후의 지표를 관리하기 위한 리포트입니다.

## 2. 테스트 환경
- **Target:** NGINX Load Balancer (Distributed to 3 app instances)
- **Tool:** k6
- **Scenario:** 결제 생성 -> 100ms 대기 -> 결제 승인

## 3. 목표 수치 (Target)
- **RPS (Request Per Second):** 2,000 이상 (Total Aggregate)
- **P95 Latency:** 100ms 이하
- **Error Rate:** 1% 미만

## 4. 테스트 결과 (2026-03-30)
| Stage | VUs | Duration | RPS (Avg) | P95 Latency | Success Rate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Load Test | 200 | 2m | TBD | TBD | TBD |

> **Note:** 위 결과는 로컬 환경 기준이며, 인프라 사양에 따라 달라질 수 있습니다.
