package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.application.dto.AiMessageProcessorDto;
import com.abba.tanahora.application.dto.MessageReceivedType;
import com.abba.tanahora.application.dto.UpgradeCheckoutResult;
import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.application.messaging.classifier.MessageClassifier;
import com.abba.tanahora.application.notification.BasicWhatsAppMessage;
import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.service.NotificationService;
import com.abba.tanahora.domain.service.SubscriptionService;
import com.abba.tanahora.domain.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(500)
@RequiredArgsConstructor
public class PlanUpgradeHandler implements MessageHandler {

    private final UserService userService;
    private final MessageClassifier messageClassifier;
    private final NotificationService notificationService;
    private final SubscriptionService subscriptionService;

    @Override
    public boolean supports(AIMessage message) {
        AiMessageProcessorDto classify = messageClassifier.classify(message);
        return classify.getType() == MessageReceivedType.PLAN_UPGRADE;
    }

    @Override
    public void handle(AIMessage message) {

        String userId = message.getWhatsappId();
        User user = userService.findByWhatsappId(userId);
        if (user == null) {
            user = userService.register(userId, message.getContactName());
        }

        UpgradeCheckoutResult checkout = subscriptionService.createOrReuseUpgradeLink(userId, message.getContactName());
        String responseMessage = buildResponseMessage(checkout);

        notificationService.sendNotification(user,
                BasicWhatsAppMessage.builder()
                        .to(user.getWhatsappId())
                        .message(responseMessage)
                        .build());
    }

    private String buildResponseMessage(UpgradeCheckoutResult checkout) {
        if (checkout.alreadyPremium()) {
            return "Seu plano Premium ja esta ativo. Obrigado por seguir com a gente.";
        }
        if (checkout.checkoutUrl() == null || checkout.checkoutUrl().isBlank()) {
            return "Nao consegui gerar seu link de pagamento agora. Tente novamente em alguns minutos.";
        }

        return String.format("""
                Oi, que bom que esta gostando.
                Para concluir seu upgrade para o Premium, acesse:
                %s
                
                Assim que o pagamento for aprovado, confirmo por aqui.
                """, checkout.checkoutUrl());
    }
}
