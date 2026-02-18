package com.abba.tanahora.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tanahora.mercadopago")
@Data
public class MercadoPagoProperties {

    private String apiBaseUrl = "https://api.mercadopago.com";
    private String publicKey;
    private String accessToken;
    private String webhookSecret;
}
