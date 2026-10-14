package com.ltld.app.llmgateway.controller;

import com.jayway.jsonpath.JsonPath;
import com.ltld.app.llmgateway.config.ApiKeyAuthFilter;
import com.ltld.app.llmgateway.config.RequestIdFilter;
import com.ltld.app.llmgateway.dto.chatdto.ChatResponse;
import com.ltld.app.llmgateway.entity.ApiClient;
import com.ltld.app.llmgateway.enums.ClientStatus;
import com.ltld.app.llmgateway.enums.FinishReason;
import com.ltld.app.llmgateway.exception.AppException;
import com.ltld.app.llmgateway.exception.ErrorCode;
import com.ltld.app.llmgateway.repository.ApiClientRepository;
import com.ltld.app.llmgateway.service.ChatService;
import com.ltld.app.llmgateway.util.ApiKeyHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
public class ChatControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiClientRepository apiClientRepository;

    @MockitoBean
    private ChatService chatService;

    private static final String VALID_BODY = """
            {"model":"chat-fast","messages":[{"role":"user","content":"hi"}]}
            """;

    private static final String TEST_KEY = "test-key";

    private ApiClient apiClient;

    @BeforeEach
    void setUp() {
         apiClient = ApiClient.builder()
                .id(3L)
                .name("test")
                .apiKeyHash(ApiKeyHasher.sha256Hex(TEST_KEY))
                .dailyTokenQuota(100L)
                .rateLimitPerMinute(10)
                .status(ClientStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void noApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(header().exists(RequestIdFilter.HEADER));
    }

    @Test
    void unknownKey_returns401() throws Exception {
        when(apiClientRepository.findByApiKeyHash(ApiKeyHasher.sha256Hex(TEST_KEY)))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .header(ApiKeyAuthFilter.HEADER, TEST_KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(header().exists(RequestIdFilter.HEADER));
    }

    @Test
    void disabledClient_returns401() throws Exception {
        apiClient.setStatus(ClientStatus.DISABLED);

        when(apiClientRepository.findByApiKeyHash(ApiKeyHasher.sha256Hex(TEST_KEY)))
                .thenReturn(Optional.of(apiClient));

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .header(ApiKeyAuthFilter.HEADER, TEST_KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(header().exists(RequestIdFilter.HEADER));
    }

    @Test
    void validKey_returns200_withSameRequestId() throws Exception {
        when(apiClientRepository.findByApiKeyHash(ApiKeyHasher.sha256Hex(TEST_KEY)))
                .thenReturn(Optional.of(apiClient));

        when(chatService.chat(any(), any(), anyString()))
                .thenAnswer(inv -> ChatResponse.builder()
                        .requestId(inv.getArgument(2))
                        .provider("groq")
                        .content("ok")
                        .finishReason(FinishReason.STOP)
                        .build());

        MvcResult result = mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY)
                        .header(ApiKeyAuthFilter.HEADER, TEST_KEY))
                .andExpect(status().isOk())
                .andReturn();

        String headerId = result.getResponse().getHeader(RequestIdFilter.HEADER);
        String bodyId = JsonPath.read(result.getResponse().getContentAsString(), "$.requestId");

        assertEquals(headerId, bodyId);
    }

    @Test
    void validKey_emptyMessages_returns400() throws Exception {
        when(apiClientRepository.findByApiKeyHash(ApiKeyHasher.sha256Hex(TEST_KEY)))
                .thenReturn(Optional.of(apiClient));

        String invalidBody = """
            {"model":"chat-fast","messages":[{}]}
            """;

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody)
                        .header(ApiKeyAuthFilter.HEADER, TEST_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void unknownModel_returns400() throws Exception {
        when(apiClientRepository.findByApiKeyHash(ApiKeyHasher.sha256Hex(TEST_KEY)))
                .thenReturn(Optional.of(apiClient));
        when(chatService.chat(any(), any(), anyString()))
                .thenThrow(new AppException(ErrorCode.MODEL_NOT_SUPPORTED));

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(ApiKeyAuthFilter.HEADER, TEST_KEY)
                        .content("""
                            {"model":"gpt-99","messages":[{"role":"user","content":"hi"}]}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MODEL_NOT_SUPPORTED"))
                .andExpect(jsonPath("$.requestId").exists());
    }
}
