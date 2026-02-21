package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.application.dto.AiMessageProcessorDto;
import com.abba.tanahora.application.dto.MessageReceivedType;
import com.abba.tanahora.application.dto.PlanInfoResult;
import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.application.messaging.classifier.MessageClassifier;
import com.abba.tanahora.application.notification.BasicWhatsAppMessage;
import com.abba.tanahora.domain.model.Plan;
import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.service.NotificationService;
import com.abba.tanahora.domain.service.SubscriptionService;
import com.abba.tanahora.domain.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
@Order(505)
@RequiredArgsConstructor
public class PlanInfoHandler implements MessageHandler {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));

    private final UserService userService;
    private final MessageClassifier messageClassifier;
    private final NotificationService notificationService;
    private final SubscriptionService subscriptionService;

    @Override
    public boolean supports(AIMessage message) {
        AiMessageProcessorDto classify = messageClassifier.classify(message);
        return classify.getType() == MessageReceivedType.PLAN_INFO;
    }

    @Override
    public void handle(AIMessage message) {
        String userId = message.getWhatsappId();
        User user = userService.findByWhatsappId(userId);
        if (user == null) {
            user = userService.register(userId, message.getContactName());
        }

        PlanInfoResult info = subscriptionService.getPlanInfo(userId, message.getContactName());

        String planStatus = info.plan() == Plan.PREMIUM ? "PREMIUM" : "FREE";
        String premiumUntil = formatPremiumUntil(info.premiumUntil());
        String messageBody = String.format("""
                Aqui estão os dados do seu plano:
                - Plano atual: %s
                - Premium até: %s
                - Assinatura: %s
                """, planStatus, premiumUntil, info.subscriptionStatus());

        notificationService.sendNotification(user,
                BasicWhatsAppMessage.builder()
                        .to(user.getWhatsappId())
                        .message(messageBody)
                        .build());
    }

    private String formatPremiumUntil(OffsetDateTime premiumUntil) {
        if (premiumUntil == null) {
            return "--";
        }
        return DATE_FORMATTER.format(premiumUntil);
    }
}
