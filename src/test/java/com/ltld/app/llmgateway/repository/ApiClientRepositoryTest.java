package com.ltld.app.llmgateway.repository;

import com.ltld.app.llmgateway.entity.ApiClient;
import com.ltld.app.llmgateway.enums.ClientStatus;
import com.ltld.app.llmgateway.util.ApiKeyHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
public class ApiClientRepositoryTest {
    @Autowired
    private ApiClientRepository repository;

    @Test
    void findsSeededDevClient() {
       ApiClient apiClient = repository.findByApiKeyHash(ApiKeyHasher.sha256Hex("sk-dev-local-123"))
               .orElseThrow();

        assertEquals("dev-local", apiClient.getName());
    }

    @Test
    void returnsEmptyForUnknownHash() {
        assertTrue(repository.findByApiKeyHash(ApiKeyHasher.sha256Hex("unknown")).isEmpty());
    }

    @Test
    void savesNewClientWithGeneratedId() {
        ApiClient apiClient = ApiClient.builder()
                .name("dev")
                .apiKeyHash("dummy-hash")
                .dailyTokenQuota(10000L)
                .rateLimitPerMinute(1000)
                .status(ClientStatus.ACTIVE)
                .build();

        ApiClient saved = repository.save(apiClient);

        assertNotNull(saved.getId());
    }
}
