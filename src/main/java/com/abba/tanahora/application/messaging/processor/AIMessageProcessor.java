package com.abba.tanahora.application.messaging.processor;

import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.domain.model.MessageReceived;
import com.abba.tanahora.domain.service.MessageReceivedHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AIMessageProcessor implements MessageReceivedHandler {

    private final MessageChain messageChain;

    @Override
    public void handle(MessageReceived messageReceived) {
        AIMessage message = AIMessage.from(messageReceived);
        messageChain.process(message);
    }

}
