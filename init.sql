-- 추가 DB 생성
CREATE DATABASE mockly_test OWNER mockly;

-- 권한 부여 (Postgres security best practice)
GRANT ALL PRIVILEGES ON DATABASE mockly TO mockly;
GRANT ALL PRIVILEGES ON DATABASE mockly_test TO mockly;
