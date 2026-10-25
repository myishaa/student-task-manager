import http from 'k6/http';
import { check } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:30080').replace(/\/$/, '');
const requestedVus = Number(__ENV.MAX_VUS || 30);
const maxVus = Math.min(30, Math.max(1, Number.isFinite(requestedVus) ? requestedVus : 30));

export const options = {
  stages: [
    { duration: '20s', target: Math.min(5, maxVus) },
    { duration: '30s', target: Math.min(15, maxVus) },
    { duration: '60s', target: maxVus },
    { duration: '20s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1500'],
  },
};

export default function () {
  const response = http.get(`${baseUrl}/api/tasks`);
  check(response, {
    'task list returns 200': (result) => result.status === 200,
    'task list returns JSON': (result) => result.headers['Content-Type']?.includes('application/json'),
  });
}
