package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.application.dto.AiMessageProcessorDto;
import com.abba.tanahora.application.dto.MessageReceivedType;
import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.application.messaging.classifier.MessageClassifier;
import com.abba.tanahora.application.notification.BasicWhatsAppMessage;
import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.service.NotificationService;
import com.abba.tanahora.domain.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
@RequiredArgsConstructor
public class WelcomeMessageHandler implements MessageHandler {

    private final MessageClassifier messageClassifier;
    private final NotificationService notificationService;
    private final UserService userService;

    @Override
    public boolean supports(AIMessage message) {
        AiMessageProcessorDto dto = messageClassifier.classify(message);
        return dto.getType() == MessageReceivedType.WELCOME;
    }

    @Override
    public void handle(AIMessage message) {

        User user = userService.register(message.getWhatsappId(), message.getContactName());

        String welcomeMessage = String.format("""
                Oi %s, tudo bem?
                
                Para começar, pode me pedir para criar um lembrete de medicamento.
                Basta me mandar o nome do medicamento, a dosagem, a frequência e a data de fim.
                
                Por exemplo:
                "Registrar um comprimido de dipirona a cada 8 horas durante 7 dias"
                
                Vamos começar?
                """, user.getName());

        notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                .to(user.getWhatsappId())
                .message(welcomeMessage)
                .build());
    }

}
