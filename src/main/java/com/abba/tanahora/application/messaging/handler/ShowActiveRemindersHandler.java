package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.application.dto.AiMessageProcessorDto;
import com.abba.tanahora.application.dto.MessageReceivedType;
import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.application.messaging.classifier.MessageClassifier;
import com.abba.tanahora.application.notification.BasicWhatsAppMessage;
import com.abba.tanahora.domain.model.Reminder;
import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.service.NotificationService;
import com.abba.tanahora.domain.service.ReminderService;
import com.abba.tanahora.domain.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Order(305)
@RequiredArgsConstructor
public class ShowActiveRemindersHandler implements MessageHandler {

    private final MessageClassifier messageClassifier;
    private final ReminderService reminderService;
    private final UserService userService;
    private final NotificationService notificationService;

    @Override
    public boolean supports(AIMessage message) {
        AiMessageProcessorDto dto = messageClassifier.classify(message);
        return dto.getType() == MessageReceivedType.SHOW_ACTIVE_REMINDERS;
    }

    @Override
    public void handle(AIMessage message) {

        User user = userService.findByWhatsappId(message.getWhatsappId());

        List<Reminder> reminders = reminderService.getByUser(user);

        String messageStr;
        if (reminders.isEmpty()) {
            messageStr = "Você ainda não tem nenhum lembrete ativo. Que tal registrar um agora?";
        } else {
            messageStr = "Esses são seus lembretes ativos no momento: " +
                    reminders.stream().map(r -> r.getMedication().getName()).collect(Collectors.joining(", "));
        }

        notificationService.sendNotification(user,
                BasicWhatsAppMessage.builder()
                        .to(user.getWhatsappId())
                        .message(messageStr)
                        .build());

    }
}
