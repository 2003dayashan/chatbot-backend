package com.example.chatbot.service;

import com.example.chatbot.model.Conversation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private final WebClient geminiWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    /**
     * Sends conversation history (already trimmed to the last N messages by ChatService)
     * plus the new user message to Gemini, and returns the reply text.
     */
    public String generateReply(List<Conversation.Message> history, String newUserMessage) {
        ObjectNode requestBody = buildRequestBody(history, newUserMessage);

        try {
            JsonNode response = geminiWebClient.post()
                    .uri(apiUrl)
                    .header("x-goog-api-key", apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            return extractReplyText(response);

        } catch (Exception e) {
            log.error("Gemini API call failed", e);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI service is currently unavailable. Please try again."
            );
        }
    }

    private ObjectNode buildRequestBody(List<Conversation.Message> history, String newUserMessage) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");

        // Prior turns for context
        for (Conversation.Message msg : history) {
            ObjectNode turn = contents.addObject();
            // Gemini expects "user" or "model" - our Message.role already matches this.
            turn.put("role", msg.getRole());
            turn.putArray("parts").addObject().put("text", msg.getContent());
        }

        // The new message being sent right now
        ObjectNode newTurn = contents.addObject();
        newTurn.put("role", "user");
        newTurn.putArray("parts").addObject().put("text", newUserMessage);

        return root;
    }

    private String extractReplyText(JsonNode response) {
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Empty response from AI service");
        }

        JsonNode candidates = response.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            // Could be blocked by safety filters - surface something useful instead of a stack trace
            JsonNode promptFeedback = response.path("promptFeedback");
            log.warn("No candidates returned. promptFeedback={}", promptFeedback);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI did not return a response for this message");
        }

        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Malformed AI response");
        }

        return parts.get(0).path("text").asText();
    }
}
