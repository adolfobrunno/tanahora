package com.abba.tanahora.infrastructure.scheduler;

import com.abba.tanahora.domain.model.MessageReceived;
import com.abba.tanahora.domain.service.MessageReceivedHandler;
import com.abba.tanahora.domain.service.MessageReceivedService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "tictacmed.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MessageReceivedJob {

    private static final Logger log = LoggerFactory.getLogger(MessageReceivedJob.class);

    private final MessageReceivedService messageReceivedService;
    private final MessageReceivedHandler messageReceivedHandler;

    @Scheduled(fixedDelayString = "${tanahora.scheduler.fixed-delay-ms:60000}")
    public void processPendingMessages() {
        List<MessageReceived> pending = messageReceivedService.getPendingMessages();
        for (MessageReceived message : pending) {
            try {
                log.debug("Processing message id={} whatsappId={}", message.getId(), message.getWhatsappId());
                messageReceivedHandler.handle(message);
                messageReceivedService.markAsProcessed(message.getId());
            } catch (Exception e) {
                log.error("Failed to process message id={} whatsappId={}", message.getId(), message.getWhatsappId(), e);
            }
        }
    }
}
