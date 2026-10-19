// 검증: "재고만큼만 발급되어야 한다" + "같은 사용자는 1장만".
// VU 500개가 총 10,000건 요청, 사용자는 1~20,000 중 랜덤 → 같은 사용자 중복 요청 발생.
// 정상이면 정확히 5,000건 발급, duplicate_users = 0.
import http from 'k6/http';

const COUPON_ID = __ENV.COUPON_ID || '1';
const USER_POOL = 20000;

export const options = {
  scenarios: { issue: {
    executor: 'shared-iterations',
    vus: 500,
    iterations: 10000,
    maxDuration: '2m',
  } },
};

export default function () {
  const userId = Math.floor(Math.random() * USER_POOL) + 1;
  http.post(`http://localhost:8080/api/coupons/${COUPON_ID}/issue`, null, {
    headers: { 'X-User-Id': String(userId) },
    tags: { name: 'issue' },
  });
}
