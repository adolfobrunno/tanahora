package com.abba.tanahora.application.messaging.processor;

import com.abba.tanahora.application.dto.AiMessageProcessorDto;
import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.application.messaging.classifier.MessageClassifier;
import com.abba.tanahora.application.messaging.handler.MessageHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MessageChain {

    private final List<MessageHandler> handlers;
    private final MessageClassifier messageClassifier;

    public boolean process(AIMessage message) {

        AiMessageProcessorDto dto = messageClassifier.classify(message);

        for (MessageHandler handler : handlers) {
            if (handler.supports(dto)) {
                handler.handle(dto);
                return true;
            }
        }
        return false;
    }
}
