package com.example.chatbot.repository;

import com.example.chatbot.model.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends MongoRepository<Conversation, String> {

    // All conversations for a user, most recently updated first (sidebar list)
    List<Conversation> findByUserIdOrderByUpdatedAtDesc(String userId);

    // Ownership-scoped lookup: prevents one user from reading another user's conversation
    // by guessing/forging an id.
    Optional<Conversation> findByIdAndUserId(String id, String userId);

    // Ownership-scoped delete, same reasoning as above.
    void deleteByIdAndUserId(String id, String userId);
}
