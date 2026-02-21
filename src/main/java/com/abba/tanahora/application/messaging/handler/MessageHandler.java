package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.application.messaging.AIMessage;
public interface MessageHandler {

    boolean supports(AIMessage message);

    void handle(AIMessage message);
}
