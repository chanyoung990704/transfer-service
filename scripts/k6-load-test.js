import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  stages: [
    { duration: '30s', target: 50 },  // Ramp-up
    { duration: '1m', target: 200 },  // Load test
    { duration: '30s', target: 0 },   // Ramp-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<100'], // 95%의 요청이 100ms 이내여야 함
    http_req_failed: ['rate<0.01'],   // 에러율 1% 미만
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost';

export default function () {
  const orderId = `ORDER-${uuidv4()}`;
  const amount = 10000;

  // 1. 결제 생성 요청
  const createRes = http.post(`${BASE_URL}/api/payments`, JSON.stringify({
    orderId: orderId,
    amount: amount
  }), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(createRes, {
    'create payment status is 200': (r) => r.status === 200,
  });

  sleep(0.1);

  // 2. 결제 승인 요청
  const confirmRes = http.post(`${BASE_URL}/api/payments/confirm`, JSON.stringify({
    orderId: orderId,
    amount: amount,
    paymentKey: `pk_${uuidv4()}`
  }), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(confirmRes, {
    'confirm payment status is 200': (r) => r.status === 200,
  });

  sleep(0.5);
}
