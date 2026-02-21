package com.abba.tanahora.application.service;

import com.abba.tanahora.application.notification.WhatsAppGateway;
import com.abba.tanahora.application.notification.WhatsAppMessage;
import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final WhatsAppGateway whatsAppGateway;


    @Override
    public String sendNotification(User user, WhatsAppMessage message) {
        return whatsAppGateway.sendMessage(user, message);
    }

}
