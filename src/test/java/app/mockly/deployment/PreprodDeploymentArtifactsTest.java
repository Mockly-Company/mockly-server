package app.mockly.deployment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PreprodDeploymentArtifactsTest {

    private static final Path COMPOSE_FILE = Path.of("deploy/preprod/docker-compose.yml");
    private static final Path SEED_FILE = Path.of("deploy/preprod/seed.sql");

    @Test
    @DisplayName("pre-prod Compose는 dev와 분리된 라우팅과 자원 제한을 사용한다")
    void composeIsIsolatedFromDev() throws IOException {
        Map<String, Object> compose = loadYaml(COMPOSE_FILE);
        Map<String, Object> services = map(compose.get("services"));
        Map<String, Object> service = map(services.get("mockly-server-preprod"));
        Map<String, Object> environment = map(service.get("environment"));
        List<String> labels = list(service.get("labels"));
        Map<String, Object> networks = map(compose.get("networks"));
        Map<String, Object> network = map(networks.get("mockly-network"));

        assertThat(service.get("container_name")).isEqualTo("mockly-server-preprod");
        assertThat(service.get("image")).isEqualTo("mockly-server-preprod:${APP_IMAGE_TAG:-latest}");
        assertThat(service.get("restart")).isEqualTo("unless-stopped");
        assertThat(service.get("mem_limit")).isEqualTo("512m");
        assertThat(service.get("cpus")).isEqualTo(0.75);
        assertThat(environment)
                .containsEntry("SPRING_PROFILES_ACTIVE", "preprod")
                .containsEntry("POSTGRES_HOST", "mockly-postgres")
                .containsEntry("POSTGRES_DB", "${POSTGRES_DB}")
                .containsEntry("REDIS_HOST", "mockly-redis")
                .containsEntry("REDIS_DATABASE", "${REDIS_DATABASE:-1}");
        assertThat(labels)
                .contains("traefik.enable=true")
                .contains("traefik.docker.network=mockly-infra_mockly-network")
                .contains("traefik.http.routers.mockly-preprod.rule=Host(`pre-prod.kancho.co`)")
                .contains("traefik.http.services.mockly-preprod.loadbalancer.server.port=8080");
        assertThat(network)
                .containsEntry("external", true)
                .containsEntry("name", "mockly-infra_mockly-network");
    }

    @Test
    @DisplayName("pre-prod Compose는 민감정보를 환경변수 참조로만 주입한다")
    void composeDoesNotContainSecretValues() throws IOException {
        Map<String, Object> compose = loadYaml(COMPOSE_FILE);
        Map<String, Object> service = map(map(compose.get("services")).get("mockly-server-preprod"));
        Map<String, Object> environment = map(service.get("environment"));

        List<String> sensitiveNames = List.of(
                "POSTGRES_PASSWORD",
                "REDIS_PASSWORD",
                "JWT_SECRET",
                "GOOGLE_ANDROID_CLIENT_ID",
                "GOOGLE_IOS_CLIENT_ID",
                "PORTONE_API_SECRET",
                "PORTONE_WEBHOOK_SECRET",
                "OPENAI_API_KEY"
        );

        for (String name : sensitiveNames) {
            assertThat(environment.get(name))
                    .as(name + " must be injected through Compose interpolation")
                    .isInstanceOf(String.class)
                    .asString()
                    .startsWith("${");
        }
    }

    @Test
    @DisplayName("pre-prod seed는 기준 데이터의 고유키를 사용해 재실행할 수 있다")
    void seedUsesIdempotentKeys() throws IOException {
        String seedSql = Files.readString(SEED_FILE);

        assertThat(seedSql)
                .contains("ON CONFLICT (name) DO UPDATE")
                .contains("ON CONFLICT (plan_id, currency) DO UPDATE")
                .contains("WHERE NOT EXISTS")
                .contains("ON CONFLICT (product_id, billing_cycle, currency) DO UPDATE")
                .contains("ON CONFLICT (plan_tier) DO UPDATE")
                .doesNotContain("INSERT INTO users")
                .doesNotContain("INSERT INTO payment")
                .doesNotContain("INSERT INTO refresh_token");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYaml(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return new Yaml().load(reader);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<String> list(Object value) {
        return (List<String>) value;
    }
}
