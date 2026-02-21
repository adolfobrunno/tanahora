package com.abba.tanahora.domain.service;

import com.abba.tanahora.domain.model.MessageReceived;

import java.util.List;

public interface MessageReceivedService {

    void markAsProcessed(String id);
    void markAsError(String id);

    List<MessageReceived> getPendingMessages();

    void receiveMessage(MessageReceived messageReceived);
}
