-- Free 플랜
INSERT INTO plan (name, description, features, billing_cycle, is_active, created_at, updated_at)
VALUES (
  'Free',
  'AI 면접 연습을 가볍게 체험해보세요',
  '["AI 면접 연습 일 1회", "AI 기본 피드백 제공", "면접 결과 요약 제공", "면접 기록 7일 보관"]'::jsonb,
  'MONTHLY',
  true,
  NOW(),
  NOW()
);

-- Basic 플랜
INSERT INTO plan (name, description, features, billing_cycle, is_active, created_at, updated_at)
VALUES (
  'Basic',
  'AI와 함께 면접을 준비하는 가장 기본적인 플랜',
  '["AI 면접 연습 일 5회", "AI 상세 피드백 제공", "면접 결과 비교 및 히스토리 관리", "면접 기록 무제한 보관", "음성 기반 AI 인터뷰 지원"]'::jsonb,
  'MONTHLY',
  true,
  NOW(),
  NOW()
);

-- Pro 플랜
INSERT INTO plan (name, description, features, billing_cycle, is_active, created_at, updated_at)
VALUES (
  'Pro',
  'AI 분석을 통해 면접 실력을 체계적으로 개선하세요',
  '["AI 면접 연습 일 10회", "AI 심층 분석 리포트 제공", "개인 맞춤형 AI 피드백", "면접 결과 추이 분석", "음성 기반 AI 인터뷰 지원"]'::jsonb,
  'MONTHLY',
  true,
  NOW(),
  NOW()
);

--
---- Premium 플랜
--INSERT INTO plan (name, description, features, billing_cycle, is_active, created_at, updated_at)
--VALUES (
--  'Premium',
--  'Premium',
--  '["AI 면접 연습 무제한", "AI 분석 리포트", "맞춤형 피드백", "음성 인터뷰 지원", "모의 면접 매칭 우선권", "기업 대시보드"]'::jsonb,
--  'MONTHLY',
--  true,
--  NOW(),
--  NOW()
--);

-- PlanPrice 테이블에 가격 정보 삽입 (KRW 기준)
-- Plan ID는 실제로는 자동 생성되므로, 서브쿼리로 찾아서 삽입

-- Free 플랜 가격
INSERT INTO plan_price (plan_id, price, currency)
SELECT id, 0, 'KRW'
FROM plan
WHERE name = 'Free';

-- Basic 플랜 가격
INSERT INTO plan_price (plan_id, price, currency)
SELECT id, 4900, 'KRW'
FROM plan
WHERE name = 'Basic';

-- Pro 플랜 가격
INSERT INTO plan_price (plan_id, price, currency)
SELECT id, 9900, 'KRW'
FROM plan
WHERE name = 'Pro';

---- Premium 플랜 가격
--INSERT INTO plan_price (plan_id, price, currency)
--SELECT id, 14900, 'KRW'
--FROM plan
--WHERE name = 'Premium';

-- ============================================
-- SubscriptionProduct (상품)
-- ============================================

-- Free 플랜 상품
INSERT INTO subscription_product (name, description, features, is_active, created_at, updated_at)
VALUES (
    'Free',
    'AI 면접 연습을 가볍게 체험해보세요',
    '["AI 면접 연습 일 1회", "AI 기본 피드백 제공", "면접 결과 요약 제공", "면접 기록 7일 보관"]'::jsonb,
    true,
    NOW(),
    NOW()
);

-- Basic 플랜 상품
INSERT INTO subscription_product (name, description, features, is_active, created_at, updated_at)
VALUES (
    'Basic',
    'AI와 함께 면접을 준비하는 가장 기본적인 플랜',
    '["AI 면접 연습 일 5회", "AI 상세 피드백 제공", "면접 결과 비교 및 히스토리 관리", "면접 기록 무제한 보관", "음성 기반 AI 인터뷰 지원"]'::jsonb,
    true,
    NOW(),
    NOW()
);

-- Pro 플랜 상품
INSERT INTO subscription_product (name, description, features, is_active, created_at, updated_at)
VALUES (
    'Pro',
    'AI 분석을 통해 면접 실력을 체계적으로 개선하세요',
    '["AI 면접 연습 일 10회", "AI 심층 분석 리포트 제공", "개인 맞춤형 AI 피드백", "면접 결과 추이 분석", "음성 기반 AI 인터뷰 지원"]'::jsonb,
    true,
    NOW(),
    NOW()
);

-- ============================================
-- SubscriptionPlan (가격 옵션)
-- ============================================

-- Free 플랜 - 평생 무료
INSERT INTO subscription_plan (product_id, amount, currency, billing_cycle)
VALUES (1, 0, 'KRW', 'LIFETIME');

-- Basic 플랜 - 월간
INSERT INTO subscription_plan (product_id, amount, currency, billing_cycle)
VALUES (2, 9900, 'KRW', 'MONTHLY');

-- Basic 플랜 - 연간 (선택사항)
-- INSERT INTO subscription_plan (product_id, amount, currency, billing_cycle)
-- VALUES (2, 99000, 'KRW', 'YEARLY');

-- Pro 플랜 - 월간
INSERT INTO subscription_plan (product_id, amount, currency, billing_cycle)
VALUES (3, 14900, 'KRW', 'MONTHLY');

-- Pro 플랜 - 연간 (선택사항)
-- INSERT INTO subscription_plan (product_id, amount, currency, billing_cycle)
-- VALUES (3, 169000, 'KRW', 'YEARLY');