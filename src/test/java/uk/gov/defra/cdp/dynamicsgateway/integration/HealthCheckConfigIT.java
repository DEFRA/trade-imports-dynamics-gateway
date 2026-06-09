package uk.gov.defra.cdp.dynamicsgateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class HealthCheckConfigIT extends IntegrationBase {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void healthEndpoint_isAccessible() {
        ResponseEntity<String> response = restTemplate.getForEntity("/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
            .containsAnyOf("application/json", "application/vnd.spring-boot.actuator.v3+json");
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void healthEndpoint_respondsQuickly() {
        long startTime = System.currentTimeMillis();

        ResponseEntity<String> response = restTemplate.getForEntity("/health", String.class);

        long duration = System.currentTimeMillis() - startTime;

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(duration)
            .as("Health check should respond quickly without database connectivity checks")
            .isLessThan(1000L);
    }

    @Test
    void healthEndpoint_noDetailsExposed() {
        ResponseEntity<String> response = restTemplate.getForEntity("/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContain("components").doesNotContain("details");
    }

    @Test
    void healthEndpoint_atRootPath() {
        ResponseEntity<String> response = restTemplate.getForEntity("/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> oldPath = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(oldPath.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/info", "/metrics", "/env", "/actuator"})
    void endpoint_notAccessible(String path) {
        ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
