package com.ltld.app.llmgateway.dto.chatdto;

import com.ltld.app.llmgateway.enums.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ChatRequest {
    @NotBlank(message = "Model is required")
    private String model;

    @NotEmpty(message = "Messages must not be empty")
    @Size(max = 50, message = "Messages must have at most 50 items")
    private List<@Valid Message> messages;

    @Min(1) @Max(8192)
    private Integer maxTokens = 1024;

    @DecimalMin("0.0") @DecimalMax("2.0")
    private Double temperature = 0.7;

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Message {
        @NotNull(message = "This field is required")
        private Role role;

        @NotBlank(message = "This field is required")
        @Size(max = 20000)
        private String content;
    }
}
