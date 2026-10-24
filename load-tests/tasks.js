import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const maxVus = Math.max(1, Number(__ENV.MAX_VUS || 5));

export const options = {
  stages: [
    { duration: '30s', target: Math.min(2, maxVus) },
    { duration: '1m', target: maxVus },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

const jsonHeaders = { headers: { 'Content-Type': 'application/json' } };

export default function () {
  const suffix = `${__VU}-${__ITER}-${Date.now()}`;
  const create = http.post(
    `${baseUrl}/api/tasks`,
    JSON.stringify({
      title: `k6 task ${suffix}`,
      description: 'Temporary task created by the k6 smoke/load test',
      priority: 'MEDIUM',
      status: 'TODO',
    }),
    jsonHeaders,
  );

  const created = check(create, {
    'task create returns 201': (response) => response.status === 201,
    'created task has an id': (response) => Boolean(response.json('id')),
  });

  if (!created) {
    sleep(1);
    return;
  }

  const taskId = create.json('id');
  check(http.get(`${baseUrl}/api/tasks/${taskId}`), {
    'task read returns 200': (response) => response.status === 200,
  });

  check(
    http.patch(
      `${baseUrl}/api/tasks/${taskId}/status`,
      JSON.stringify({ status: 'DONE' }),
      jsonHeaders,
    ),
    { 'task status update returns 200': (response) => response.status === 200 },
  );

  check(http.del(`${baseUrl}/api/tasks/${taskId}`), {
    'task cleanup returns 204': (response) => response.status === 204,
  });

  sleep(1);
}
