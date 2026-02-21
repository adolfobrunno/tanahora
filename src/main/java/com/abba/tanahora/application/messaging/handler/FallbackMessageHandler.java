package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.application.messaging.AIMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1000)
@Slf4j
public class FallbackMessageHandler implements MessageHandler {

    @Override
    public boolean supports(AIMessage message) {
        return true;
    }

    @Override
    public void handle(AIMessage message) {
        log.info("No handler matched message id={} whatsappId={}", message.getId(), message.getWhatsappId());
    }
}
