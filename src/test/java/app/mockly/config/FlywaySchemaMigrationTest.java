package app.mockly.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FlywaySchemaMigrationTest {

    @Test
    void migrations_create_weekly_plan_and_subscription_lifecycle_schema() throws Exception {
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
            assertThat(columnExists(metaData, "subscription", "cancel_at_period_end")).isTrue();
            assertThat(columnExists(metaData, "subscription", "scheduled_plan_id")).isTrue();
            assertThat(columnExists(metaData, "subscription", "payment_method_id")).isTrue();
            assertThat(indexExists(metaData, "subscription", "uk_subscription_current_user")).isTrue();
            assertThat(indexExists(metaData, "subscription", "idx_subscription_cancel_period_end")).isTrue();
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

            try (var freeProduct = connection.createStatement().executeQuery("""
                    SELECT weekly_interview_limit, features
                    FROM subscription_product
                    WHERE plan_tier = 'FREE'
                    """)) {
                assertThat(freeProduct.next()).isTrue();
                assertThat(freeProduct.getInt(1)).isEqualTo(1);
                assertThat(freeProduct.getString(2))
                        .contains("기본 면접 히스토리 조회")
                        .doesNotContain("면접 기록 7일 보관");
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
