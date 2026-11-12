ALTER TABLE subscription
    ADD COLUMN cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE subscription
    ADD COLUMN scheduled_plan_id INTEGER;

ALTER TABLE subscription
    ADD COLUMN payment_method_id BIGINT;

ALTER TABLE subscription
    ADD CONSTRAINT fk_subscription_scheduled_plan
        FOREIGN KEY (scheduled_plan_id) REFERENCES subscription_plan (id);

ALTER TABLE subscription
    ADD CONSTRAINT fk_subscription_payment_method
        FOREIGN KEY (payment_method_id) REFERENCES payment_method (id);

CREATE INDEX idx_subscription_cancel_period_end
    ON subscription (status, cancel_at_period_end, current_period_end);

ALTER TABLE payment_method
    ALTER COLUMN billing_key TYPE VARCHAR(512);

UPDATE subscription_product
SET features = CAST('["AI 면접 연습 주 1회", "AI 기본 피드백 제공", "면접 결과 요약 제공", "기본 면접 히스토리 조회"]' AS JSONB),
    updated_at = CURRENT_TIMESTAMP
WHERE plan_tier = 'FREE';
