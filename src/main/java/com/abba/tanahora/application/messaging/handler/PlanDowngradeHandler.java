package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.application.dto.AiMessageProcessorDto;
import com.abba.tanahora.application.dto.DowngradeResult;
import com.abba.tanahora.application.dto.MessageReceivedType;
import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.application.messaging.classifier.MessageClassifier;
import com.abba.tanahora.application.messaging.flow.FlowState;
import com.abba.tanahora.application.notification.BasicWhatsAppMessage;
import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.service.NotificationService;
import com.abba.tanahora.domain.service.SubscriptionService;
import com.abba.tanahora.domain.service.UserService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(510)
public class PlanDowngradeHandler implements HandleAndFlushMessageHandler {

    private final UserService userService;
    private final MessageClassifier messageClassifier;
    private final NotificationService notificationService;
    private final SubscriptionService subscriptionService;

    public PlanDowngradeHandler(UserService userService,
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
        return classify.getType() == MessageReceivedType.PLAN_DOWNGRADE;
    }

    @Override
    public void handleAndFlush(AIMessage message, FlowState state) {
        String userId = state.getUserId();
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
