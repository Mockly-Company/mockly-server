package app.mockly.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import java.sql.DriverManager;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class FlywaySchemaMigrationTest {

    @Test
    void v1_creates_the_weekly_plan_schema_without_payapp_checkout() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:flyway-schema;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("classpath:db/migration")
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
            assertThat(columnExists(metaData, "subscription", "current_marker")).isTrue();
            assertThat(indexExists(metaData, "subscription", "uk_subscription_current_user")).isTrue();
            assertThat(tableExists(metaData, "interview_quota")).isFalse();
            assertThat(tableExists(metaData, "payment_checkout")).isFalse();

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
        try (ResultSet indexes = metaData.getIndexInfo(null, null, tableName, true, false)) {
            while (indexes.next()) {
                if (indexName.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
