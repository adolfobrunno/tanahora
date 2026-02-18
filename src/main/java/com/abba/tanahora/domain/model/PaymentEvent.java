package com.abba.tanahora.domain.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;

@Document(collection = "payment_events")
@Data
public class PaymentEvent {

    @Id
    private String id;

    @Indexed(unique = true)
    private String eventKey;
    private String gateway;
    private String eventType;
    private String resourceId;
    private String payload;
    private boolean processed;
    private OffsetDateTime processedAt;
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
