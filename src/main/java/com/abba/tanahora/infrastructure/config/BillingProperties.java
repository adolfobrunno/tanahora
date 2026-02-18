package com.abba.tanahora.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "tanahora.billing")
@Data
public class BillingProperties {

    private BigDecimal premiumAmount = new BigDecimal("9.99");
    private String premiumCurrency = "BRL";
    private int premiumIntervalMonths = 1;
    private int checkoutExpiresHours = 24;
    private String checkoutBackUrl = "https://tanahora.app/checkout";
    private int nonRenewalToleranceDays = 1;
    private String reconciliationCron = "0 0 3 * * *";
}
