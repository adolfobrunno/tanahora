package com.abba.tanahora.domain.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;

@Document("messages_received")
@Data
public class MessageReceived {

    @Id
    private String id;
    private String whatsappId;
    private String contactName;
    private String messageType;
    private String message;
    private String interactiveButtonId;
    private String repliedTo;
    private String mediaId;
    private String mediaMimeType;
    private String mediaFilename;
    private String mediaSha256;
    private MessageReceivedStatus status = MessageReceivedStatus.PENDING;
    private OffsetDateTime receivedAt = OffsetDateTime.now();
    private OffsetDateTime processedAt;

    @DBRef
    ReminderEvent reminderEvent;

    public void markAsProcessed() {
        this.status = MessageReceivedStatus.PROCESSED;
        this.processedAt = OffsetDateTime.now();
    }

    public void markAsError() {
        this.status = MessageReceivedStatus.ERROR;
        this.processedAt = OffsetDateTime.now();
    }

}
