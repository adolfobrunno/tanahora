package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.application.dto.AiMessageProcessorDto;
import com.abba.tanahora.application.dto.MessageReceivedType;
import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.application.messaging.classifier.MessageClassifier;
import com.abba.tanahora.application.notification.BasicWhatsAppMessage;
import com.abba.tanahora.domain.model.Reminder;
import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.service.NotificationService;
import com.abba.tanahora.domain.service.PatientResolverService;
import com.abba.tanahora.domain.service.ReminderService;
import com.abba.tanahora.domain.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
@Slf4j
@Order(300)
@RequiredArgsConstructor
public class CheckNextDispatchHandler implements MessageHandler {

    private final MessageClassifier messageClassifier;
    private final ReminderService reminderService;
    private final UserService userService;
    private final NotificationService notificationService;


    @Override
    public boolean supports(AIMessage message) {
        AiMessageProcessorDto dto = messageClassifier.classify(message);
        return dto.getType() == MessageReceivedType.CHECK_NEXT_DISPATCH;
    }

    @Override
    public void handle(AIMessage message) {
        String userId = message.getWhatsappId();
        log.info("Checking next dispatch for user={}", userId);
        User user = userService.findByWhatsappId(userId);

        reminderService.getByUser(user)
                .stream()
                .min(Comparator.comparing(Reminder::getNextDispatch))
                .ifPresentOrElse(
                        reminder -> notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                                .to(user.getWhatsappId())
                                .message(reminder.createNextDispatchMessage())
                                .build()),
                        () -> notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                                .to(user.getWhatsappId())
                                .message("""
                                        Você não tem nenhum medicamento agendado.
                                        
                                        Que tal começar registrando um agora mesmo?
                                        """)
                                .build()));


    }
}
