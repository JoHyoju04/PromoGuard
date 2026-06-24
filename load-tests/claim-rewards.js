import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    reward_claims: {
      executor: 'shared-iterations',
      vus: 1000,
      iterations: 1000,
      maxDuration: '1m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const promotionId = __ENV.PROMOTION_ID || '1';
const rewardId = __ENV.REWARD_ID || '1';

export default function () {
  const userId = `user-${__VU}`;
  const response = http.post(
    `${baseUrl}/api/promotions/${promotionId}/rewards/${rewardId}/claim`,
    null,
    {
      headers: {
        'X-User-Id': userId,
      },
    },
  );

  check(response, {
    'known response': (res) => [200, 409].includes(res.status),
    'claim or limit result': (res) => {
      if (res.status === 200) {
        return res.json('status') === 'CLAIMED';
      }
      return ['SOLD_OUT', 'USER_LIMIT_EXCEEDED', 'LOCK_CONFLICT'].includes(res.json('code'));
    },
  });
}
