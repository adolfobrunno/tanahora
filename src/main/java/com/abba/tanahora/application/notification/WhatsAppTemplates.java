package com.abba.tanahora.application.notification;

import lombok.Getter;

public enum WhatsAppTemplates {

    SEND_REMINDER("en_US"),
    RECALL_TO_ACTION("pt_BR");

    @Getter
    private final String language;

    WhatsAppTemplates(String language) {
        this.language = language;
    }

    public String getTemplateName() {
        return this.name().toLowerCase();
    }
}
