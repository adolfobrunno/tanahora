package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.application.dto.AiMessageProcessorDto;
import com.abba.tanahora.application.dto.DowngradeResult;
import com.abba.tanahora.application.dto.MessageReceivedType;
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
@Order(510)
@RequiredArgsConstructor
public class PlanDowngradeHandler implements MessageHandler {

    private final UserService userService;
    private final MessageClassifier messageClassifier;
    private final NotificationService notificationService;
    private final SubscriptionService subscriptionService;

    @Override
    public boolean supports(AIMessage message) {
        AiMessageProcessorDto classify = messageClassifier.classify(message);
        return classify.getType() == MessageReceivedType.PLAN_DOWNGRADE;
    }

    @Override
    public void handle(AIMessage message) {
        String userId = message.getWhatsappId();
        User user = userService.findByWhatsappId(userId);
        if (user == null) {
            user = userService.register(userId, message.getContactName());
        }

        DowngradeResult result = subscriptionService.requestDowngrade(userId, message.getContactName());
        String response = buildResponse(result);
        notificationService.sendNotification(user,
                BasicWhatsAppMessage.builder()
                        .to(user.getWhatsappId())
                        .message(response)
                        .build());
    }

    private String buildResponse(DowngradeResult result) {
        if (result.alreadyFree()) {
            return "Ops, você não tem assinatura ativa no momento. Para assinar o Premium e aproveitar os benefícios, é só me avisar!";
        }
        if (result.cancellationConfirmed()) {
            return "Renovação automática cancelada. Seu Premium segue ativo até o fim do período atual.";
        }
        if (result.cancellationRequested()) {
            return "Recebi seu pedido de cancelamento. Vou confirmar o encerramento da renovação automática e te aviso por aqui.";
        }
        return "Não encontrei assinatura recorrente ativa para cancelar.";
    }
}
