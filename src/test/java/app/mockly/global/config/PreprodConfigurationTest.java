package app.mockly.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PreprodConfigurationTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    @DisplayName("공통 설정은 Redis database index 환경변수를 지원한다")
    void commonConfigurationSupportsRedisDatabaseIndex() throws IOException {
        PropertySource<?> common = load("application.yaml");

        assertThat(common.getProperty("spring.data.redis.database"))
                .isEqualTo("${REDIS_DATABASE:0}");
    }

    @Test
    @DisplayName("pre-prod 설정은 민감한 개발 로그와 Swagger UI를 비활성화한다")
    void preprodConfigurationDisablesDevelopmentDiagnostics() throws IOException {
        PropertySource<?> preprod = load("application-preprod.yaml");

        assertThat(preprod.getProperty("spring.jpa.show-sql")).isEqualTo(false);
        assertThat(preprod.getProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(preprod.getProperty("springdoc.api-docs.enabled")).isEqualTo(false);
        assertThat(preprod.getProperty("springdoc.swagger-ui.enabled")).isEqualTo(false);
        assertThat(preprod.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(preprod.getProperty("logging.level.root")).isEqualTo("INFO");
        assertThat(preprod.getProperty("logging.level.app.mockly")).isEqualTo("INFO");
        assertThat(preprod.getProperty("logging.level.org.springframework.security.web.FilterChainProxy"))
                .isEqualTo("INFO");
        assertThat(preprod.getProperty("logging.level.org.springframework.security.web.util.matcher"))
                .isEqualTo("INFO");
    }

    private PropertySource<?> load(String resourceName) throws IOException {
        return loader.load(
                resourceName,
                new FileSystemResource("src/main/resources/" + resourceName)
        ).getFirst();
    }
}
