package app.mockly.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FlywaySchemaMigrationTest {

    @Test
    void v1_and_v3_create_account_lifecycle_and_multi_user_device_session_schema() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:flyway-schema;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("classpath:db/migration")
                .outOfOrder(true)
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            DatabaseMetaData metaData = connection.getMetaData();

            assertThat(tableExists(metaData, "subscription_product")).isTrue();
            assertThat(tableExists(metaData, "quota_usage")).isTrue();
            assertThat(tableExists(metaData, "plan")).isFalse();
            assertThat(tableExists(metaData, "plan_price")).isFalse();
            assertThat(columnExists(metaData, "subscription_product", "weekly_interview_limit")).isTrue();
            assertThat(columnExists(metaData, "subscription_product", "weekly_improvement_practice_limit")).isTrue();
            assertThat(columnExists(metaData, "subscription", "past_due_at")).isTrue();
            assertThat(columnExists(metaData, "subscription", "activated_at")).isTrue();
            assertThat(columnExists(metaData, "subscription", "current_marker")).isTrue();
            assertThat(indexExists(metaData, "subscription", "uk_subscription_current_user")).isTrue();
            assertThat(tableExists(metaData, "interview_quota")).isFalse();
            assertThat(tableExists(metaData, "payment_checkout")).isFalse();
            assertThat(columnExists(metaData, "interview_session", "feedback_generation_tier")).isTrue();
            assertThat(columnExists(metaData, "interview_session", "feedback_generation_task_id")).isTrue();
            assertThat(indexExists(metaData, "interview_session", "idx_interview_session_feedback_recovery")).isTrue();
            assertThat(columnExists(metaData, "interview_feedback", "coach_brief_summary")).isTrue();
            assertThat(columnExists(metaData, "interview_feedback", "score_structure")).isTrue();
            assertThat(columnExists(metaData, "interview_feedback", "generated_tier")).isTrue();
            assertThat(columnExists(metaData, "interview_feedback", "expert_feedbacks")).isFalse();
            assertThat(columnExists(metaData, "interview_feedback", "strengths")).isFalse();
            assertThat(columnExists(metaData, "interview_feedback", "improvements")).isFalse();
            assertThat(columnExists(metaData, "interview_feedback", "detailed_analysis")).isFalse();
            assertThat(tableExists(metaData, "feedback_strength")).isTrue();
            assertThat(tableExists(metaData, "feedback_improvement")).isTrue();
            assertThat(columnType(metaData, "interview_feedback", "score_structure")).isEqualTo("INTEGER");
            assertThat(columnType(metaData, "feedback_strength", "question_number")).isEqualTo("INTEGER");
            assertThat(columnType(metaData, "feedback_improvement", "rank")).isEqualTo("INTEGER");

            // V3: users profile/terms/withdrawal columns + user_agreement table + multi-user session constraint
            assertThat(columnExists(metaData, "users", "job_category")).isTrue();
            assertThat(columnExists(metaData, "users", "job_role")).isTrue();
            assertThat(columnExists(metaData, "users", "experience_years")).isTrue();
            assertThat(columnExists(metaData, "users", "terms_agreed_at")).isTrue();
            assertThat(columnExists(metaData, "users", "deleted_at")).isTrue();
            assertThat(tableExists(metaData, "user_agreement")).isTrue();
            assertThat(columnExists(metaData, "user_agreement", "agreement_type")).isTrue();
            assertThat(columnExists(metaData, "user_agreement", "agreed")).isTrue();
            assertThat(columnExists(metaData, "user_agreement", "agreed_at")).isTrue();

            UUID userA = UUID.randomUUID();
            UUID userB = UUID.randomUUID();
            try (var stmt = connection.createStatement()) {
                stmt.executeUpdate("""
                        INSERT INTO users (id, provider, provider_id, email, name, created_at, updated_at)
                        VALUES ('%s', 'GOOGLE', 'sub-a', 'a@example.com', 'User A', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                               ('%s', 'APPLE', 'sub-b', 'b@example.com', 'User B', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """.formatted(userA, userB));
                stmt.executeUpdate("""
                        INSERT INTO session (user_id, device_id, device_name, created_at, updated_at)
                        VALUES ('%s', 'shared-iphone-device', 'iPhone 16', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                               ('%s', 'shared-iphone-device', 'iPhone 16', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """.formatted(userA, userB));
            }

            try (var freeLimit = connection.createStatement().executeQuery("""
                    SELECT weekly_interview_limit
                    FROM subscription_product
                    WHERE plan_tier = 'FREE'
                    """)) {
                freeLimit.next();
                assertThat(freeLimit.getInt(1)).isEqualTo(1);
            }
            try (var proLimit = connection.createStatement().executeQuery("""
                    SELECT weekly_improvement_practice_limit
                    FROM subscription_product
                    WHERE plan_tier = 'PRO'
                    """)) {
                proLimit.next();
                assertThat(proLimit.getInt(1)).isEqualTo(4);
            }
        }
    }

    @Test
    void v3_succeeds_even_when_legacy_session_device_constraint_was_already_dropped_or_renamed() {
        String jdbcUrl = "jdbc:h2:mem:flyway-v3-legacy-constraint;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("classpath:db/migration")
                .target("1")
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             var stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE session DROP CONSTRAINT uk_session_device_id");
            stmt.executeUpdate("ALTER TABLE session ADD CONSTRAINT session_device_id_key UNIQUE (device_id)");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("classpath:db/migration")
                .outOfOrder(true)
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             var stmt = connection.createStatement()) {
            UUID u1 = UUID.randomUUID();
            UUID u2 = UUID.randomUUID();
            stmt.executeUpdate("""
                    INSERT INTO users (id, provider, provider_id, email, name, created_at, updated_at)
                    VALUES ('%s', 'GOOGLE', 'g-1', 'g1@example.com', 'G1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                           ('%s', 'APPLE', 'a-2', 'a2@example.com', 'A2', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """.formatted(u1, u2));
            stmt.executeUpdate("""
                    INSERT INTO session (user_id, device_id, device_name, created_at, updated_at)
                    VALUES ('%s', 'same-device-id', 'iPhone', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                           ('%s', 'same-device-id', 'iPhone', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """.formatted(u1, u2));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean tableExists(DatabaseMetaData metaData, String tableName) throws Exception {
        try (ResultSet tables = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private boolean columnExists(DatabaseMetaData metaData, String tableName, String columnName) throws Exception {
        try (ResultSet columns = metaData.getColumns(null, null, tableName, columnName)) {
            return columns.next();
        }
    }

    private boolean indexExists(DatabaseMetaData metaData, String tableName, String indexName) throws Exception {
        try (ResultSet indexes = metaData.getIndexInfo(null, null, tableName, false, false)) {
            while (indexes.next()) {
                if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private String columnType(DatabaseMetaData metaData, String tableName, String columnName) throws Exception {
        try (ResultSet columns = metaData.getColumns(null, null, tableName, columnName)) {
            assertThat(columns.next()).isTrue();
            return columns.getString("TYPE_NAME");
        }
    }
}
