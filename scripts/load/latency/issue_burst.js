// 측정: "발급 폭증" 시나리오. 사용자 응답 P99 와 DB 쓰기 QPS 를 본다.
// 2단원 (동기) → v2a/v2b/v2c (큐 디커플링) 비교의 기준 시나리오.
//
// USER_POOL 이 재고 (5000) 보다 충분히 커서 대부분의 요청은 매진 (409 SOLD_OUT)
// 응답을 받는다. 우리가 보고 싶은 것은 "응답 시간 분포" 자체이지 발급 성공 수가 아니다.
import http from 'k6/http';
import { Trend, Counter } from 'k6/metrics'; // Trend: 분포 (avg/p95/p99), Counter: 누적 합

const COUPON_ID = __ENV.COUPON_ID || '1';        // k6 run -e COUPON_ID=... 로 주입
const USER_POOL = 20000;                          // 재고 5000 << 사용자 20000 → 대부분 매진 응답
const BASE = __ENV.BASE_URL || 'http://localhost:8080';

// 응답 시간 분포를 기록할 커스텀 메트릭. 두 번째 인자 true 는 "시간 단위로 표시"
const issueLatency = new Trend('issue_latency', true);
// 상태 코드별 누적 카운터. k6 요약에 status_2xx=5000 처럼 노출된다.
const status2xx = new Counter('status_2xx');
const status409 = new Counter('status_409_sold_out_or_dup');
const statusOther = new Counter('status_other');
// 서버가 연결을 끊은 요청 (Tomcat 연결 한도 초과 등). 응답이 없어 레이턴시에서 제외한다.
const connReset = new Counter('conn_reset');

export const options = {
  scenarios: {
    burst: {
      // constant-arrival-rate: VU 수와 무관하게 "초당 N건" 을 강제로 쏟아부음.
      // 백엔드가 느려도 요청 페이스가 떨어지지 않아 진짜 부하 측정에 적합.
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 3000), timeUnit: '1s', duration: '30s', // RATE req/s × 30s (기본 3000 → 총 90,000 요청)
      preAllocatedVUs: 2000, maxVUs: Number(__ENV.MAX_VUS || 5000),         // 동시 처리에 쓸 가상 사용자 풀 (요청 단위는 rate 가 결정)
    },
  },
  thresholds: {
    // 큐 디커플링 효과 확인용 임계. v2a/b/c 에서는 통과, 2단원 (동기) 에서는 깨지는 게 정상.
    'issue_latency': ['p(99)<500'],
  },
};

// 각 VU 가 반복 실행하는 단위. constant-arrival-rate 가 호출 횟수를 결정.
export default function () {
  const userId = Math.floor(Math.random() * USER_POOL) + 1;     // 1 ~ USER_POOL 중 랜덤
  const res = http.post(`${BASE}/api/coupons/${COUPON_ID}/issue`, null, {
    headers: { 'X-User-Id': String(userId) },                    // 인증 대신 헤더로 사용자 식별
  });
  if (res.status === 0) { connReset.add(1); return; }            // 연결 끊김: 레이턴시에 섞이면 p99 가 좋아 보임
  issueLatency.add(res.timings.duration);                        // ms 단위 응답 시간 기록
  if (res.status >= 200 && res.status < 300) status2xx.add(1);   // 발급 성공
  else if (res.status === 409) status409.add(1);                 // 매진 or 중복 발급 (예상된 거절)
  else statusOther.add(1);                                       // 5xx 등 비정상
}