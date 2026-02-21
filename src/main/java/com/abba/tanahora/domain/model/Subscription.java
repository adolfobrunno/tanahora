package com.abba.tanahora.domain.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Document(collection = "subscriptions")
@CompoundIndex(name = "user_status_idx", def = "{'whatsappId': 1, 'status': 1}")
@Data
public class Subscription {

    @Id
    private String id;

    @Indexed
    private String userId;
    @Indexed
    private String whatsappId;

    private Plan plan = Plan.PREMIUM;
    private BigDecimal amount;
    private String currency;
    private Integer intervalMonths;

    private SubscriptionStatus status = SubscriptionStatus.PENDING;

    @Indexed
    private String gatewaySubscriptionId;
    @Indexed
    private String gatewayPaymentLinkId;
    private String checkoutUrl;
    private OffsetDateTime checkoutExpiresAt;
    private OffsetDateTime activatedAt;
    private OffsetDateTime nextBillingAt;
    private OffsetDateTime lastChargeApprovedAt;
    private String lastPaymentId;
    private boolean cancellationRequested;
    private OffsetDateTime cancellationRequestedAt;
    private OffsetDateTime cancellationConfirmedAt;

    private OffsetDateTime createdAt = OffsetDateTime.now();
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
