package com.abba.tanahora.application.service;

import com.abba.tanahora.domain.model.MessageReceived;
import com.abba.tanahora.domain.model.MessageReceivedStatus;
import com.abba.tanahora.domain.repository.MessageReceivedRepository;
import com.abba.tanahora.domain.service.MessageReceivedService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageReceivedServiceImpl implements MessageReceivedService {

    private final MessageReceivedRepository messageReceivedRepository;

    @Override
    public void receiveMessage(MessageReceived messageReceived) {
        messageReceived.setStatus(MessageReceivedStatus.PENDING);
        messageReceived.setReceivedAt(OffsetDateTime.now());
        messageReceivedRepository.save(messageReceived);
    }

    @Override
    public void markAsProcessed(String id) {
        messageReceivedRepository.findById(id)
                .ifPresent(messageReceived -> {
                    messageReceived.markAsProcessed();
                    messageReceivedRepository.save(messageReceived);
                });
    }

    @Override
    public void markAsError(String id) {
        messageReceivedRepository.findById(id)
                .ifPresent(messageReceived -> {
                    messageReceived.markAsError();
                    messageReceivedRepository.save(messageReceived);
                });
    }

    @Override
    public List<MessageReceived> getPendingMessages() {
        return messageReceivedRepository.findAllPending();
    }
}
