package com.example.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {

    @NotBlank(message = "Message cannot be empty")
    private String message;

    // Null/omitted => backend creates a brand new conversation
    private String conversationId;
}
