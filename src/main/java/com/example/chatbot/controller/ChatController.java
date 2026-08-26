package com.example.chatbot.controller;

import com.example.chatbot.dto.ChatRequest;
import com.example.chatbot.dto.ChatResponse;
import com.example.chatbot.model.Conversation;
import com.example.chatbot.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/send")
    public ResponseEntity<ChatResponse> sendMessage(
            Authentication authentication,
            @Valid @RequestBody ChatRequest request
    ) {
        String userId = authentication.getName();
        ChatResponse response = chatService.sendMessage(userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    public ResponseEntity<List<Conversation>> getHistory(Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(chatService.getHistory(userId));
    }

    @GetMapping("/history/{id}")
    public ResponseEntity<Conversation> getConversation(
            Authentication authentication,
            @PathVariable String id
    ) {
        String userId = authentication.getName();
        return ResponseEntity.ok(chatService.getConversation(userId, id));
    }

    @DeleteMapping("/history/{id}")
    public ResponseEntity<Void> deleteConversation(
            Authentication authentication,
            @PathVariable String id
    ) {
        String userId = authentication.getName();
        chatService.deleteConversation(userId, id);
        return ResponseEntity.noContent().build();
    }
}
