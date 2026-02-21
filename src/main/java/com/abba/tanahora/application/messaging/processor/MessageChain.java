package com.abba.tanahora.application.messaging.processor;

import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.application.messaging.handler.MessageHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MessageChain {

    private final List<MessageHandler> handlers;

    public boolean process(AIMessage message) {

        for (MessageHandler handler : handlers) {
            if (handler.supports(message)) {
                handler.handle(message);
                return true;
            }
        }
        return false;
    }
}
