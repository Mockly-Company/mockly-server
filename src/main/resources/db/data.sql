INSERT INTO plan (name, description, features, billing_cycle, is_active, created_at, updated_at) VALUES
    ('Free', 'AI 면접 연습을 가볍게 체험해보세요', '["AI 면접 연습 주 1회", "AI 기본 피드백 제공", "면접 결과 요약 제공", "면접 기록 7일 보관"]'::jsonb, 'MONTHLY', true, NOW(), NOW()),
    ('Basic', 'AI와 함께 면접을 준비하는 가장 기본적인 플랜', '["AI 면접 연습 주 4회", "AI 상세 피드백 제공", "면접 결과 비교 및 히스토리 관리", "면접 기록 무제한 보관", "음성 기반 AI 인터뷰 지원"]'::jsonb, 'MONTHLY', true, NOW(), NOW()),
    ('Pro', 'AI 분석을 통해 면접 실력을 체계적으로 개선하세요', '["AI 면접 연습 주 10회", "AI 심층 분석 리포트 제공", "개선 연습 주 4회", "개인 맞춤형 AI 피드백", "면접 결과 추이 분석", "음성 기반 AI 인터뷰 지원"]'::jsonb, 'MONTHLY', true, NOW(), NOW());

INSERT INTO plan_price (plan_id, price, currency) SELECT id, 0, 'KRW' FROM plan WHERE name = 'Free';
INSERT INTO plan_price (plan_id, price, currency) SELECT id, 4900, 'KRW' FROM plan WHERE name = 'Basic';
INSERT INTO plan_price (plan_id, price, currency) SELECT id, 9900, 'KRW' FROM plan WHERE name = 'Pro';

INSERT INTO subscription_product (name, description, features, plan_tier, is_active, max_questions, weekly_interview_limit, weekly_improvement_practice_limit, created_at, updated_at) VALUES
    ('Free', 'AI 면접 연습을 가볍게 체험해보세요', '["AI 면접 연습 주 1회", "AI 기본 피드백 제공", "면접 결과 요약 제공", "면접 기록 7일 보관"]'::jsonb, 'FREE', true, 3, 1, 0, NOW(), NOW()),
    ('Basic', 'AI와 함께 면접을 준비하는 가장 기본적인 플랜', '["AI 면접 연습 주 4회", "AI 상세 피드백 제공", "면접 결과 비교 및 히스토리 관리", "면접 기록 무제한 보관", "음성 기반 AI 인터뷰 지원"]'::jsonb, 'BASIC', true, 5, 4, 0, NOW(), NOW()),
    ('Pro', 'AI 분석을 통해 면접 실력을 체계적으로 개선하세요', '["AI 면접 연습 주 10회", "AI 심층 분석 리포트 제공", "개선 연습 주 4회", "개인 맞춤형 AI 피드백", "면접 결과 추이 분석", "음성 기반 AI 인터뷰 지원"]'::jsonb, 'PRO', true, 7, 10, 4, NOW(), NOW());

INSERT INTO subscription_plan (product_id, price, currency, billing_cycle) SELECT id, 0, 'KRW', 'MONTHLY' FROM subscription_product WHERE plan_tier = 'FREE';
INSERT INTO subscription_plan (product_id, price, currency, billing_cycle) SELECT id, 5900, 'KRW', 'MONTHLY' FROM subscription_product WHERE plan_tier = 'BASIC';
INSERT INTO subscription_plan (product_id, price, currency, billing_cycle) SELECT id, 9900, 'KRW', 'MONTHLY' FROM subscription_product WHERE plan_tier = 'PRO';

INSERT INTO interview_quota (plan_tier, daily_limit, max_questions_per_session)
VALUES ('FREE', 1, 3), ('BASIC', 5, 5), ('PRO', 15, 10);
