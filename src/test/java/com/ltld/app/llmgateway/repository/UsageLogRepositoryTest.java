package com.ltld.app.llmgateway.repository;

import com.ltld.app.llmgateway.entity.ApiClient;
import com.ltld.app.llmgateway.entity.UsageLog;
import com.ltld.app.llmgateway.enums.FinishReason;
import com.ltld.app.llmgateway.enums.UsageStatus;
import com.ltld.app.llmgateway.exception.ErrorCode;
import com.ltld.app.llmgateway.util.ApiKeyHasher;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
class UsageLogRepositoryTest {

    @Autowired private UsageLogRepository repository;
    @Autowired private ApiClientRepository clientRepository;
    @Autowired private EntityManager em;

    private ApiClient devClient() {
        return clientRepository.findByApiKeyHash(ApiKeyHasher.sha256Hex("sk-dev-local-123")).orElseThrow();
    }

    private UsageLog.UsageLogBuilder base(String requestId) {
        return UsageLog.builder()
                .requestId(requestId)
                .client(devClient())
                .requestedModel("chat-fast")
                .latencyMs(1234);
    }

    @Test
    void savesSuccess_roundTripsFromDb() {
        UsageLog saved = repository.saveAndFlush(base("req_test_ok")
                .status(UsageStatus.SUCCESS)
                .provider("groq").actualModel("openai/gpt-oss-120b")
                .inputTokens(10L).outputTokens(5L).totalTokens(15L)
                .finishReason(FinishReason.STOP)
                .attemptCount(1)
                .build());

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());

        em.clear();   // xóa cache của Hibernate → lần đọc sau phải truy vấn DB thật

        UsageLog found = repository.findByRequestId("req_test_ok").orElseThrow();
        assertEquals(UsageStatus.SUCCESS, found.getStatus());
        assertEquals(15L, found.getTotalTokens());
        assertEquals(FinishReason.STOP, found.getFinishReason());
        assertNull(found.getErrorCode());
    }

    @Test
    void savesError_withoutProviderOrTokens() {
        repository.saveAndFlush(base("req_test_err")
                .status(UsageStatus.ERROR)
                .errorCode(ErrorCode.MODEL_NOT_SUPPORTED)
                .build());
        em.clear();

        UsageLog found = repository.findByRequestId("req_test_err").orElseThrow();
        assertEquals(ErrorCode.MODEL_NOT_SUPPORTED, found.getErrorCode());
        assertNull(found.getProvider());
        assertNull(found.getTotalTokens());
        assertEquals(0, found.getAttemptCount());
        assertFalse(found.isFallbackUsed());
    }

    @Test
    void successWithErrorCode_rejectedByDb() {
        UsageLog bad = base("req_bad_1").status(UsageStatus.SUCCESS)
                .errorCode(ErrorCode.PROVIDER_ERROR).build();

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(bad));
    }

    @Test
    void errorWithoutErrorCode_rejectedByDb() {
        UsageLog bad = base("req_bad_2").status(UsageStatus.ERROR).build();

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(bad));
    }

    @Test
    void duplicateRequestId_rejectedByDb() {
        repository.saveAndFlush(base("req_dup").status(UsageStatus.SUCCESS).build());

        UsageLog dup = base("req_dup").status(UsageStatus.SUCCESS).build();
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(dup));
    }
}