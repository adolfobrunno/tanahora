package com.abba.tanahora.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tanahora.asaas")
@Data
public class AsaasProperties {

    private String apiBaseUrl = "https://sandbox.asaas.com/api/v3";
    private String apiKey;
    private String webhookToken;
    private String billingType = "UNDEFINED";
    private String defaultCustomerId;
}
