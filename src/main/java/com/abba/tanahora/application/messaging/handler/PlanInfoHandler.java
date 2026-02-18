package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.application.dto.AiMessageProcessorDto;
import com.abba.tanahora.application.dto.MessageReceivedType;
import com.abba.tanahora.application.dto.PlanInfoResult;
import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.application.messaging.classifier.MessageClassifier;
import com.abba.tanahora.application.messaging.flow.FlowState;
import com.abba.tanahora.application.notification.BasicWhatsAppMessage;
import com.abba.tanahora.domain.model.Plan;
import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.service.NotificationService;
import com.abba.tanahora.domain.service.SubscriptionService;
import com.abba.tanahora.domain.service.UserService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
@Order(505)
public class PlanInfoHandler implements HandleAndFlushMessageHandler {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"));

    private final UserService userService;
    private final MessageClassifier messageClassifier;
    private final NotificationService notificationService;
    private final SubscriptionService subscriptionService;

    public PlanInfoHandler(UserService userService,
                           MessageClassifier messageClassifier,
                           NotificationService notificationService,
                           SubscriptionService subscriptionService) {
        this.userService = userService;
        this.messageClassifier = messageClassifier;
        this.notificationService = notificationService;
        this.subscriptionService = subscriptionService;
    }

    @Override
    public boolean supports(AIMessage message, FlowState state) {
        AiMessageProcessorDto classify = messageClassifier.classify(message, state);
        return classify.getType() == MessageReceivedType.PLAN_INFO;
    }

    @Override
    public void handleAndFlush(AIMessage message, FlowState state) {
        String userId = state.getUserId();
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
