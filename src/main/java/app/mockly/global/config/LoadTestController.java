package app.mockly.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("loadtest")
@RequiredArgsConstructor
public class LoadTestController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/api/loadtest/db-ping")
    public ResponseEntity<String> dbPing() {
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return ResponseEntity.ok("pong");
    }
}
