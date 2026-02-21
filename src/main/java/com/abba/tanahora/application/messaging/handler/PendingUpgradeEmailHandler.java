package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.application.dto.UpgradeCheckoutResult;
import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.application.notification.BasicWhatsAppMessage;
import com.abba.tanahora.domain.model.PendingUserAction;
import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.service.NotificationService;
import com.abba.tanahora.domain.service.SubscriptionService;
import com.abba.tanahora.domain.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(95)
@RequiredArgsConstructor
public class PendingUpgradeEmailHandler implements MessageHandler {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);

    private final UserService userService;
    private final SubscriptionService subscriptionService;
    private final NotificationService notificationService;

    @Override
    public boolean supports(AIMessage message) {
        if (message == null || message.getWhatsappId() == null || message.getWhatsappId().isBlank()) {
            return false;
        }
        User user = userService.findByWhatsappId(message.getWhatsappId());
        return user != null && user.getPendingAction() == PendingUserAction.UPGRADE_EMAIL;
    }

    @Override
    public void handle(AIMessage message) {
        User user = userService.findByWhatsappId(message.getWhatsappId());
        if (user == null) {
            return;
        }

        String email = extractEmail(message.getBody());
        if (email == null) {
            notificationService.sendNotification(user,
                    BasicWhatsAppMessage.builder()
                            .to(user.getWhatsappId())
                            .message("Para continuar, precisamos de um email válido. Pode me informar?")
                            .build());
            return;
        }

        user.setEmail(email);
        user.setPendingAction(null);
        user.setPendingActionCreatedAt(null);
        userService.save(user);

        UpgradeCheckoutResult checkout = subscriptionService.createOrReuseUpgradeLink(user.getWhatsappId(), message.getContactName());
        String responseMessage = buildResponseMessage(checkout);
        notificationService.sendNotification(user,
                BasicWhatsAppMessage.builder()
                        .to(user.getWhatsappId())
                        .message(responseMessage)
                        .build());
    }

    private String extractEmail(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = EMAIL_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(0);
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
