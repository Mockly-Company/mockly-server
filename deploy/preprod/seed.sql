BEGIN;

INSERT INTO plan (name, description, features, billing_cycle, is_active, created_at, updated_at)
VALUES
    (
        'Free',
        'AI 면접 연습을 가볍게 체험해보세요',
        '["AI 면접 연습 일 1회", "AI 기본 피드백 제공", "면접 결과 요약 제공", "면접 기록 7일 보관"]'::jsonb,
        'MONTHLY',
        true,
        NOW(),
        NOW()
    ),
    (
        'Basic',
        'AI와 함께 면접을 준비하는 가장 기본적인 플랜',
        '["AI 면접 연습 일 5회", "AI 상세 피드백 제공", "면접 결과 비교 및 히스토리 관리", "면접 기록 무제한 보관", "음성 기반 AI 인터뷰 지원"]'::jsonb,
        'MONTHLY',
        true,
        NOW(),
        NOW()
    ),
    (
        'Pro',
        'AI 분석을 통해 면접 실력을 체계적으로 개선하세요',
        '["AI 면접 연습 일 10회", "AI 심층 분석 리포트 제공", "개인 맞춤형 AI 피드백", "면접 결과 추이 분석", "음성 기반 AI 인터뷰 지원"]'::jsonb,
        'MONTHLY',
        true,
        NOW(),
        NOW()
    )
ON CONFLICT (name) DO UPDATE
SET description = EXCLUDED.description,
    features = EXCLUDED.features,
    billing_cycle = EXCLUDED.billing_cycle,
    is_active = EXCLUDED.is_active,
    updated_at = NOW();

INSERT INTO plan_price (plan_id, price, currency)
SELECT id,
       CASE name
           WHEN 'Free' THEN 0
           WHEN 'Basic' THEN 4900
           WHEN 'Pro' THEN 9900
       END,
       'KRW'
FROM plan
WHERE name IN ('Free', 'Basic', 'Pro')
ON CONFLICT (plan_id, currency) DO UPDATE
SET price = EXCLUDED.price;

UPDATE subscription_product
SET name = source.name,
    description = source.description,
    features = source.features,
    is_active = true,
    updated_at = NOW()
FROM (
    VALUES
        (
            'FREE',
            'Free',
            'AI 면접 연습을 가볍게 체험해보세요',
            '["AI 면접 연습 일 1회", "AI 기본 피드백 제공", "면접 결과 요약 제공", "면접 기록 7일 보관"]'::jsonb
        ),
        (
            'BASIC',
            'Basic',
            'AI와 함께 면접을 준비하는 가장 기본적인 플랜',
            '["AI 면접 연습 일 5회", "AI 상세 피드백 제공", "면접 결과 비교 및 히스토리 관리", "면접 기록 무제한 보관", "음성 기반 AI 인터뷰 지원"]'::jsonb
        ),
        (
            'PRO',
            'Pro',
            'AI 분석을 통해 면접 실력을 체계적으로 개선하세요',
            '["AI 면접 연습 일 10회", "AI 심층 분석 리포트 제공", "개인 맞춤형 AI 피드백", "면접 결과 추이 분석", "음성 기반 AI 인터뷰 지원"]'::jsonb
        )
) AS source(plan_tier, name, description, features)
WHERE subscription_product.plan_tier::text = source.plan_tier;

INSERT INTO subscription_product (
    name,
    description,
    features,
    plan_tier,
    is_active,
    created_at,
    updated_at
)
SELECT source.name,
       source.description,
       source.features,
       source.plan_tier,
       true,
       NOW(),
       NOW()
FROM (
    VALUES
        (
            'FREE',
            'Free',
            'AI 면접 연습을 가볍게 체험해보세요',
            '["AI 면접 연습 일 1회", "AI 기본 피드백 제공", "면접 결과 요약 제공", "면접 기록 7일 보관"]'::jsonb
        ),
        (
            'BASIC',
            'Basic',
            'AI와 함께 면접을 준비하는 가장 기본적인 플랜',
            '["AI 면접 연습 일 5회", "AI 상세 피드백 제공", "면접 결과 비교 및 히스토리 관리", "면접 기록 무제한 보관", "음성 기반 AI 인터뷰 지원"]'::jsonb
        ),
        (
            'PRO',
            'Pro',
            'AI 분석을 통해 면접 실력을 체계적으로 개선하세요',
            '["AI 면접 연습 일 10회", "AI 심층 분석 리포트 제공", "개인 맞춤형 AI 피드백", "면접 결과 추이 분석", "음성 기반 AI 인터뷰 지원"]'::jsonb
        )
) AS source(plan_tier, name, description, features)
WHERE NOT EXISTS (
    SELECT 1
    FROM subscription_product existing
    WHERE existing.plan_tier::text = source.plan_tier
);

INSERT INTO subscription_plan (product_id, price, currency, billing_cycle)
SELECT id,
       CASE plan_tier::text
           WHEN 'FREE' THEN 0
           WHEN 'BASIC' THEN 9900
           WHEN 'PRO' THEN 14900
       END,
       'KRW',
       CASE plan_tier::text
           WHEN 'FREE' THEN 'LIFETIME'
           ELSE 'MONTHLY'
       END
FROM subscription_product
WHERE plan_tier::text IN ('FREE', 'BASIC', 'PRO')
ON CONFLICT (product_id, billing_cycle, currency) DO UPDATE
SET price = EXCLUDED.price;

INSERT INTO interview_quota (plan_tier, daily_limit, max_questions_per_session)
VALUES
    ('FREE', 1, 3),
    ('BASIC', 5, 5),
    ('PRO', 15, 10)
ON CONFLICT (plan_tier) DO UPDATE
SET daily_limit = EXCLUDED.daily_limit,
    max_questions_per_session = EXCLUDED.max_questions_per_session;

COMMIT;
