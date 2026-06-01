/*
 * 시나리오 C: 피드백 비동기 처리 검증용 k6 부하 테스트
 *
 * 목적:
 *   submitAnswer()의 AI 호출을 트랜잭션 밖으로 분리한 후
 *   POST /answer가 커넥션 풀 고갈 없이 빠르게 응답하는지 검증한다.
 *
 *   시나리오 A (pool=10, 동기): submit-answer 28% 실패, db-ping p95=5,013ms
 *   시나리오 C (pool=10, 비동기): 0% 실패, p95 < 500ms 예상
 *
 * 실행 전 준비:
 *   1. Spring Boot 애플리케이션을 http://localhost:8080 에서 실행합니다.
 *   2. 리포지토리 루트에 loadtest-fixtures.json 파일이 있어야 합니다.
 *      (기존 fixture 재사용 가능 — 세션당 1문항 구성)
 *   3. k6는 handleSummary()에서 디렉터리를 만들 수 없으므로 결과 디렉터리를 먼저 만듭니다.
 *
 * 실행:
 *   mkdir -p results
 *   k6 run loadtest/scenario-c-async-feedback.js
 *
 * 선택 환경 변수:
 *   BASE_URL=http://localhost:8080
 *   RESULT_PATH=results/scenario-c.json
 */

import http from 'k6/http';
import exec from 'k6/execution';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RESULT_PATH = __ENV.RESULT_PATH || 'results/scenario-c.json';
const EXPECTED_USER_COUNT = 30;
const ANSWER_BODY = JSON.stringify({ content: '테스트 답변입니다.' });

const fixtures = JSON.parse(open('../loadtest-fixtures.json'));

validateFixtures(fixtures);

export const options = {
  scenarios: {
    attacker: {
      executor: 'ramping-vus',
      exec: 'attacker',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 10 },
        { duration: '2m', target: 10 },
        { duration: '1m', target: 20 },
        { duration: '2m', target: 20 },
        { duration: '1m', target: 30 },
        { duration: '2m', target: 30 },
        { duration: '1m', target: 45 },
        { duration: '2m', target: 45 },
        { duration: '1m', target: 60 },
        { duration: '2m', target: 60 },
      ],
      gracefulRampDown: '0s',
    },
    victim: {
      executor: 'constant-vus',
      exec: 'victim',
      vus: 5,
      duration: '15m',
    },
  },
  thresholds: {
    // 비동기화 후 AI 호출이 트랜잭션 밖에서 실행되므로 p95 500ms 이내여야 한다
    'http_req_duration{endpoint:submit-answer}': ['p(95)<500'],
    'http_req_failed{endpoint:submit-answer}': ['rate<0.01'],
    // db-ping: 커넥션 풀 고갈이 없으면 SELECT 1은 항상 500ms 이내
    'http_req_duration{endpoint:db-ping}': ['p(95)<500'],
    'http_req_failed{endpoint:db-ping}': ['rate<0.01'],
  },
};

export function attacker() {
  const globalIter = exec.scenario.iterationInTest;
  const sessionsPerUser = fixtures[0].sessionIds.length;
  const userIndex = Math.floor(globalIter / sessionsPerUser) % fixtures.length;
  const sessionIndex = globalIter % sessionsPerUser;

  const user = fixtures[userIndex];
  const sessionId = user.sessionIds[sessionIndex];

  const totalSessions = fixtures.length * sessionsPerUser;
  if (globalIter >= totalSessions) {
    sleep(5);
    return;
  }

  const response = http.post(
    `${BASE_URL}/api/interviews/${sessionId}/answer`,
    ANSWER_BODY,
    {
      headers: {
        Authorization: `Bearer ${user.token}`,
        'Content-Type': 'application/json',
      },
      tags: { endpoint: 'submit-answer', scenario: exec.scenario.name },
      timeout: '5s', // 비동기화 후 5s 내 응답이 없으면 실패로 간주
    },
  );

  const ok = check(response, {
    'submit-answer 상태 코드가 2xx이다': (res) => res.status >= 200 && res.status < 300,
    'submit-answer 피드백 대기 또는 진행 중이다': (res) => {
      try {
        const body = JSON.parse(res.body);
        const status = body?.data?.sessionStatus;
        return status === 'FEEDBACK_PENDING' || status === 'IN_PROGRESS';
      } catch (_) {
        return false;
      }
    },
  });

  if (!ok) {
    console.error(`[submit-answer] ${response.status} — sessionId=${sessionId} body=${response.body?.substring(0, 200)}`);
  }
}

export function victim() {
  const response = http.get(`${BASE_URL}/api/loadtest/db-ping`, {
    tags: { endpoint: 'db-ping', scenario: exec.scenario.name },
    timeout: '10s',
  });

  check(response, {
    'db-ping 상태 코드가 200이다': (res) => res.status === 200,
  });

  sleep(1);
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data),
    [RESULT_PATH]: JSON.stringify(data, null, 2),
  };
}

function textSummary(data) {
  const m = data.metrics;
  const lines = [
    '',
    '=== k6 summary (Scenario C: 비동기 피드백) ===',
    '',
    '[ submit-answer ]',
    `  duration ...: ${formatTrend(m['http_req_duration{endpoint:submit-answer}'])}`,
    `  failed .....: ${formatRate(m['http_req_failed{endpoint:submit-answer}'])}`,
    `  reqs .......: ${formatCount(m['http_reqs{endpoint:submit-answer}'])}`,
    '',
    '[ db-ping ]',
    `  duration ...: ${formatTrend(m['http_req_duration{endpoint:db-ping}'])}`,
    `  failed .....: ${formatRate(m['http_req_failed{endpoint:db-ping}'])}`,
    `  reqs .......: ${formatCount(m['http_reqs{endpoint:db-ping}'])}`,
    '',
    '[ overall ]',
    `  checks .....: ${formatRate(m.checks)}`,
    `  vus_max ....: ${formatGauge(m.vus_max)}`,
    '',
  ];
  return `${lines.join('\n')}\n`;
}

function formatRate(metric) {
  if (!metric || !metric.values) return 'n/a';
  const { rate = 0, passes = 0, fails = 0 } = metric.values;
  return `${(rate * 100).toFixed(2)}% (${passes} / ${passes + fails})`;
}

function formatCount(metric) {
  if (!metric || !metric.values) return 'n/a';
  return String(metric.values.count ?? 0);
}

function formatTrend(metric) {
  if (!metric || !metric.values) return 'n/a';
  const v = metric.values;
  return `avg=${formatMs(v.avg)} min=${formatMs(v.min)} med=${formatMs(v.med)} p(95)=${formatMs(v['p(95)'])} max=${formatMs(v.max)}`;
}

function formatGauge(metric) {
  if (!metric || !metric.values) return 'n/a';
  return String(metric.values.value ?? metric.values.max ?? 'n/a');
}

function formatMs(value) {
  if (value === undefined || value === null || Number.isNaN(value)) return 'n/a';
  return `${Number(value).toFixed(2)}ms`;
}

function validateFixtures(value) {
  if (!Array.isArray(value) || value.length === 0) {
    throw new Error('fixtures는 하나 이상의 사용자를 담은 배열이어야 합니다.');
  }

  if (value.length !== EXPECTED_USER_COUNT) {
    throw new Error(`fixtures는 정확히 ${EXPECTED_USER_COUNT}명의 사용자를 담아야 합니다. 현재: ${value.length}명`);
  }

  for (const [index, user] of value.entries()) {
    if (!user || typeof user !== 'object') {
      throw new Error(`fixtures[${index}] 항목은 객체여야 합니다.`);
    }

    if (!user.userId || typeof user.userId !== 'string') {
      throw new Error(`fixtures[${index}].userId가 비어 있거나 문자열이 아닙니다.`);
    }

    if (!user.token || typeof user.token !== 'string') {
      throw new Error(`fixtures[${index}].token이 비어 있거나 문자열이 아닙니다.`);
    }

    if (!Array.isArray(user.sessionIds) || user.sessionIds.length === 0) {
      throw new Error(`fixtures[${index}].sessionIds는 하나 이상의 sessionId를 담은 배열이어야 합니다.`);
    }
  }
}
