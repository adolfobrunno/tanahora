package com.abba.tanahora.domain.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;
import java.util.UUID;

@Document(collection = "reminder_taken_history")
@Data
public class ReminderTakenHistory {

    @Id
    private UUID id = UUID.randomUUID();

    @Indexed
    private String userId;

    @Indexed
    private String patientName;

    private String medicationName;
    private OffsetDateTime takenAt = OffsetDateTime.now();
    private UUID eventId;
}
