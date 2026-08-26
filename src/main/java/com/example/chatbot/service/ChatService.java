package com.example.chatbot.service;

import com.example.chatbot.dto.ChatRequest;
import com.example.chatbot.dto.ChatResponse;
import com.example.chatbot.model.Conversation;
import com.example.chatbot.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final GeminiService geminiService;

    @Value("${gemini.max.context.messages}")
    private int maxContextMessages;

    public ChatResponse sendMessage(String userId, ChatRequest request) {
        Conversation conversation = resolveConversation(userId, request.getConversationId());

        // Take the last N messages (before adding the new one) as context for Gemini
        List<Conversation.Message> history = lastN(conversation.getMessages(), maxContextMessages);

        String replyText = geminiService.generateReply(history, request.getMessage());

        LocalDateTime now = LocalDateTime.now();

        Conversation.Message userMessage = Conversation.Message.builder()
                .role("user")
                .content(request.getMessage())
                .timestamp(now)
                .build();

        Conversation.Message aiMessage = Conversation.Message.builder()
                .role("model")
                .content(replyText)
                .timestamp(LocalDateTime.now())
                .build();

        conversation.getMessages().add(userMessage);
        conversation.getMessages().add(aiMessage);
        conversation.setUpdatedAt(LocalDateTime.now());

        // Auto-title new conversations from the first user message
        if (conversation.getTitle() == null || conversation.getTitle().isBlank()) {
            conversation.setTitle(truncateTitle(request.getMessage()));
        }

        Conversation saved = conversationRepository.save(conversation);

        return ChatResponse.builder()
                .conversationId(saved.getId())
                .reply(replyText)
                .timestamp(aiMessage.getTimestamp())
                .build();
    }

    public List<Conversation> getHistory(String userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public Conversation getConversation(String userId, String conversationId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    public void deleteConversation(String userId, String conversationId) {
        // Confirm ownership first so we return 404 instead of silently no-op-ing
        // on someone else's conversation id.
        conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        conversationRepository.deleteByIdAndUserId(conversationId, userId);
    }

    private Conversation resolveConversation(String userId, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Conversation.builder()
                    .userId(userId)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
        }

        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    private List<Conversation.Message> lastN(List<Conversation.Message> messages, int n) {
        if (messages.size() <= n) {
            return messages;
        }
        return messages.subList(messages.size() - n, messages.size());
    }

    private String truncateTitle(String message) {
        String trimmed = message.trim();
        return trimmed.length() <= 50 ? trimmed : trimmed.substring(0, 50) + "...";
    }
}
