package com.abba.tanahora.application.notification;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TemplateWhatsAppMessage implements WhatsAppMessage {

    private static final String MESSAGING_PRODUCT = "whatsapp";
    private static final String TYPE = "template";
    private static final String DEFAULT_LANGUAGE = "pt_BR";

    private final String to;
    private final String templateName;
    private final String languageCode;
    private final List<String> bodyParameters;

    private TemplateWhatsAppMessage(Builder builder) {
        this.to = builder.to;
        this.templateName = builder.templateName;
        this.languageCode = builder.languageCode == null || builder.languageCode.isBlank()
                ? DEFAULT_LANGUAGE
                : builder.languageCode;
        this.bodyParameters = List.copyOf(builder.bodyParameters);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String buildPayload() {
        if (templateName == null || templateName.isBlank()) {
            return "";
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messaging_product", MESSAGING_PRODUCT);
        payload.put("to", to);
        payload.put("type", TYPE);

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", languageCode));

        List<Map<String, Object>> components = buildComponents();
        if (!components.isEmpty()) {
            template.put("components", components);
        }

        payload.put("template", template);
        return writeJson(payload);
    }

    @Override
    public WhatsAppMessageType getType() {
        return WhatsAppMessageType.TEMPLATE;
    }

    private List<Map<String, Object>> buildComponents() {
        if (bodyParameters.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> parameters = new ArrayList<>();
        for (String value : bodyParameters) {
            if (value == null) {
                continue;
            }
            parameters.add(Map.of("type", "text", "text", value));
        }

        if (parameters.isEmpty()) {
            return List.of();
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "body");
        body.put("parameters", parameters);

        return List.of(body);
    }

    private String writeJson(Object payload) {
        try {
            return new ObjectMapper().writeValueAsString(payload);
        } catch (Exception e) {
            return "";
        }
    }

    public static final class Builder {
        private String to;
        private String templateName;
        private String languageCode;
        private final List<String> bodyParameters = new ArrayList<>();

        public Builder to(String to) {
            this.to = to;
            return this;
        }

        public Builder template(WhatsAppTemplates template) {
            this.templateName = template.getTemplateName();
            this.languageCode = template.getLanguage();
            return this;
        }

        public Builder bodyParameter(String value) {
            this.bodyParameters.add(value);
            return this;
        }

        public TemplateWhatsAppMessage build() {
            return new TemplateWhatsAppMessage(this);
        }
    }
}
