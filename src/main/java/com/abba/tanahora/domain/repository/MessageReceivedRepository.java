package com.abba.tanahora.domain.repository;

import com.abba.tanahora.domain.model.MessageReceived;
import com.abba.tanahora.domain.model.MessageReceivedStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MessageReceivedRepository extends MongoRepository<MessageReceived, String> {

    List<MessageReceived> findAllByStatus(MessageReceivedStatus status);

    MessageReceived findTopByWhatsappIdOrderByReceivedAtDesc(String whatsappId);

    default List<MessageReceived> findAllPending() {
        return findAllByStatus(MessageReceivedStatus.PENDING);
    }

}
