\if :{?expected_database}
\else
\echo 'expected_database psql variable is required'
\quit
\endif

SELECT current_database() = :'expected_database' AS database_matches \gset
\if :database_matches
\else
\echo 'refusing migration: connected database does not match expected_database'
\quit
\endif

\set ON_ERROR_STOP on

BEGIN;

DO $$
BEGIN
    IF to_regclass('public.interview_quota') IS NULL
        OR to_regclass('public.plan') IS NULL
        OR to_regclass('public.plan_price') IS NULL THEN
        RAISE EXCEPTION 'legacy schema precondition failed';
    END IF;

    IF to_regclass('public.quota_usage') IS NOT NULL
        OR to_regclass('public.feedback_strength') IS NOT NULL
        OR to_regclass('public.feedback_improvement') IS NOT NULL THEN
        RAISE EXCEPTION 'target schema objects already exist';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM subscription
        WHERE status IN ('ACTIVE', 'PAST_DUE')
        GROUP BY user_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'multiple current subscriptions exist for a user';
    END IF;
END
$$;

ALTER TABLE subscription_product
    ADD COLUMN max_questions INTEGER,
    ADD COLUMN weekly_interview_limit INTEGER,
    ADD COLUMN weekly_improvement_practice_limit INTEGER;

UPDATE subscription_product
SET name = 'Free',
    description = 'AI 면접 연습을 가볍게 체험해보세요',
    features = '["AI 면접 연습 주 1회", "AI 기본 피드백 제공", "면접 결과 요약 제공", "면접 기록 7일 보관"]'::jsonb,
    is_active = TRUE,
    max_questions = 3,
    weekly_interview_limit = 1,
    weekly_improvement_practice_limit = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE plan_tier = 'FREE';

UPDATE subscription_product
SET name = 'Basic',
    description = 'AI와 함께 면접을 준비하는 가장 기본적인 플랜',
    features = '["AI 면접 연습 주 4회", "AI 상세 피드백 제공", "면접 결과 비교 및 히스토리 관리", "면접 기록 무제한 보관", "음성 기반 AI 인터뷰 지원"]'::jsonb,
    is_active = TRUE,
    max_questions = 5,
    weekly_interview_limit = 4,
    weekly_improvement_practice_limit = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE plan_tier = 'BASIC';

UPDATE subscription_product
SET name = 'Pro',
    description = 'AI 분석을 통해 면접 실력을 체계적으로 개선하세요',
    features = '["AI 면접 연습 주 10회", "AI 심층 분석 리포트 제공", "개선 연습 주 4회", "개인 맞춤형 AI 피드백", "면접 결과 추이 분석", "음성 기반 AI 인터뷰 지원"]'::jsonb,
    is_active = TRUE,
    max_questions = 7,
    weekly_interview_limit = 10,
    weekly_improvement_practice_limit = 4,
    updated_at = CURRENT_TIMESTAMP
WHERE plan_tier = 'PRO';

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM subscription_product WHERE plan_tier IN ('FREE', 'BASIC', 'PRO')) <> 3
        OR EXISTS (
            SELECT 1
            FROM subscription_product
            WHERE max_questions IS NULL
               OR weekly_interview_limit IS NULL
               OR weekly_improvement_practice_limit IS NULL
        ) THEN
        RAISE EXCEPTION 'subscription product policy migration failed';
    END IF;
END
$$;

ALTER TABLE subscription_product
    ALTER COLUMN max_questions SET NOT NULL,
    ALTER COLUMN weekly_interview_limit SET NOT NULL,
    ALTER COLUMN weekly_improvement_practice_limit SET NOT NULL;

UPDATE subscription_plan plan
SET billing_cycle = 'MONTHLY',
    price = 0,
    currency = 'KRW'
FROM subscription_product product
WHERE plan.product_id = product.id
  AND product.plan_tier = 'FREE';

UPDATE subscription_plan plan
SET price = 5900,
    currency = 'KRW'
FROM subscription_product product
WHERE plan.product_id = product.id
  AND product.plan_tier = 'BASIC'
  AND plan.billing_cycle = 'MONTHLY';

UPDATE subscription_plan plan
SET price = 9900,
    currency = 'KRW'
FROM subscription_product product
WHERE plan.product_id = product.id
  AND product.plan_tier = 'PRO'
  AND plan.billing_cycle = 'MONTHLY';

ALTER TABLE subscription
    ADD COLUMN activated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN past_due_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN current_marker BOOLEAN;

WITH subscription_anchor AS (
    SELECT id,
           COALESCE(current_period_start, started_at, created_at AT TIME ZONE 'Asia/Seoul') AS anchor_at
    FROM subscription
    WHERE status IN ('ACTIVE', 'PAST_DUE')
), current_period AS (
    SELECT id,
           anchor_at,
           (
               EXTRACT(YEAR FROM AGE(LOCALTIMESTAMP, anchor_at))::INTEGER * 12
               + EXTRACT(MONTH FROM AGE(LOCALTIMESTAMP, anchor_at))::INTEGER
           ) AS elapsed_months
    FROM subscription_anchor
)
UPDATE subscription subscription
SET activated_at = COALESCE(subscription.created_at, CURRENT_TIMESTAMP),
    current_period_start = current_period.anchor_at
        + MAKE_INTERVAL(months => GREATEST(current_period.elapsed_months, 0)),
    current_period_end = current_period.anchor_at
        + MAKE_INTERVAL(months => GREATEST(current_period.elapsed_months, 0) + 1),
    current_marker = TRUE
FROM current_period
WHERE subscription.id = current_period.id;

UPDATE subscription
SET current_marker = NULL
WHERE status NOT IN ('ACTIVE', 'PAST_DUE');

ALTER TABLE subscription DROP CONSTRAINT IF EXISTS subscription_status_check;
ALTER TABLE subscription
    ADD CONSTRAINT ck_subscription_current_marker
        CHECK (current_marker IS NULL OR current_marker = TRUE);
CREATE UNIQUE INDEX uk_subscription_current_user
    ON subscription (user_id, current_marker);

DROP TABLE interview_quota;

CREATE TABLE quota_usage (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    user_id UUID NOT NULL,
    quota_type VARCHAR(30) NOT NULL,
    period_start DATE NOT NULL,
    used_count INTEGER NOT NULL,
    CONSTRAINT pk_quota_usage PRIMARY KEY (id),
    CONSTRAINT uk_quota_usage_user_type_period UNIQUE (user_id, quota_type, period_start)
);

ALTER TABLE interview_session
    ADD COLUMN ended_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN feedback_generation_tier VARCHAR(16),
    ADD COLUMN feedback_generation_task_id UUID;

UPDATE interview_session
SET status = 'ABANDONED',
    ended_at = updated_at
WHERE status = 'IN_PROGRESS';

UPDATE interview_session session
SET status = 'FEEDBACK_PENDING',
    feedback_status = 'FAILED',
    feedback_generation_tier = 'FREE',
    feedback_generation_task_id = NULL,
    ended_at = COALESCE(session.completed_at, session.updated_at),
    fail_reason = '기존 피드백은 추후 새 형식으로 다시 생성합니다.'
WHERE EXISTS (
    SELECT 1
    FROM interview_feedback feedback
    WHERE feedback.session_id = session.id
);

CREATE INDEX idx_interview_session_feedback_recovery
    ON interview_session (feedback_status, updated_at);
CREATE INDEX idx_interview_session_user_ended_at
    ON interview_session (user_id, ended_at);

DROP TABLE interview_feedback;

CREATE TABLE interview_feedback (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    session_id UUID NOT NULL,
    overall_score INTEGER NOT NULL,
    coach_brief_summary VARCHAR(200) NOT NULL,
    coach_brief_key_strength VARCHAR(100),
    coach_brief_key_improvement VARCHAR(100),
    score_structure INTEGER NOT NULL,
    score_specificity INTEGER NOT NULL,
    score_job_relevance INTEGER NOT NULL,
    score_clarity INTEGER NOT NULL,
    next_practice_point VARCHAR(80),
    generated_tier VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_interview_feedback PRIMARY KEY (id),
    CONSTRAINT uk_interview_feedback_session UNIQUE (session_id),
    CONSTRAINT fk_interview_feedback_session FOREIGN KEY (session_id) REFERENCES interview_session (id),
    CONSTRAINT ck_interview_feedback_overall_score CHECK (overall_score BETWEEN 1 AND 100),
    CONSTRAINT ck_interview_feedback_score_structure CHECK (score_structure BETWEEN 1 AND 100),
    CONSTRAINT ck_interview_feedback_score_specificity CHECK (score_specificity BETWEEN 1 AND 100),
    CONSTRAINT ck_interview_feedback_score_job_relevance CHECK (score_job_relevance BETWEEN 1 AND 100),
    CONSTRAINT ck_interview_feedback_score_clarity CHECK (score_clarity BETWEEN 1 AND 100)
);

CREATE TABLE feedback_strength (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    feedback_id BIGINT NOT NULL,
    question_number INTEGER NOT NULL,
    title VARCHAR(120) NOT NULL,
    detail TEXT NOT NULL,
    quote TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_feedback_strength PRIMARY KEY (id),
    CONSTRAINT fk_feedback_strength_feedback FOREIGN KEY (feedback_id) REFERENCES interview_feedback (id) ON DELETE CASCADE,
    CONSTRAINT uk_feedback_strength_order UNIQUE (feedback_id, sort_order),
    CONSTRAINT ck_feedback_strength_question CHECK (question_number > 0),
    CONSTRAINT ck_feedback_strength_order CHECK (sort_order BETWEEN 1 AND 3)
);

CREATE TABLE feedback_improvement (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    feedback_id BIGINT NOT NULL,
    rank INTEGER NOT NULL,
    question_number INTEGER NOT NULL,
    title VARCHAR(120) NOT NULL,
    summary VARCHAR(200) NOT NULL,
    detail TEXT,
    quote TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_feedback_improvement PRIMARY KEY (id),
    CONSTRAINT fk_feedback_improvement_feedback FOREIGN KEY (feedback_id) REFERENCES interview_feedback (id) ON DELETE CASCADE,
    CONSTRAINT uk_feedback_improvement_rank UNIQUE (feedback_id, rank),
    CONSTRAINT ck_feedback_improvement_question CHECK (question_number > 0),
    CONSTRAINT ck_feedback_improvement_rank CHECK (rank BETWEEN 1 AND 3)
);

DROP TABLE plan_price;
DROP TABLE plan;

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM subscription WHERE status IN ('ACTIVE', 'PAST_DUE'))
       <> (SELECT COUNT(*) FROM subscription WHERE current_marker = TRUE) THEN
        RAISE EXCEPTION 'current subscription marker migration failed';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM subscription current_subscription
        JOIN interview_session session ON session.user_id = current_subscription.user_id
        WHERE current_subscription.current_marker = TRUE
          AND session.created_at >= (
              current_subscription.current_period_start
              + MAKE_INTERVAL(
                  days => (
                      FLOOR(
                          EXTRACT(EPOCH FROM (
                              (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')
                              - current_subscription.current_period_start
                          )) / 604800
                      )::INTEGER * 7
                  )
              )
          ) AT TIME ZONE 'Asia/Seoul'
    ) THEN
        RAISE EXCEPTION 'current weekly interview usage exists; quota_usage must be migrated explicitly';
    END IF;

    IF (SELECT COUNT(*) FROM interview_feedback) <> 0 THEN
        RAISE EXCEPTION 'legacy feedback rows remain';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM interview_session
        WHERE status = 'FEEDBACK_PENDING'
          AND (feedback_status <> 'FAILED' OR feedback_generation_tier <> 'FREE')
    ) THEN
        RAISE EXCEPTION 'deferred feedback state migration failed';
    END IF;
END
$$;

COMMIT;
