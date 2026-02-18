package com.abba.tanahora.domain.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Document("prescription_imports")
@Data
public class PrescriptionImport {

    @Id
    private String id;

    @Indexed
    private String whatsappId;
    private String contactName;
    private String sourceMessageId;
    private String mediaType;
    private String mediaId;
    private String mediaMimeType;
    private String mediaFilename;
    private String mediaSha256;
    private String caption;

    private PrescriptionImportStatus status = PrescriptionImportStatus.PROCESSING;
    private String interactiveMessageId;
    private String errorMessage;

    private List<PrescriptionExtractedReminder> extractedReminders = new ArrayList<>();

    private OffsetDateTime createdAt = OffsetDateTime.now();
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public void touch() {
        this.updatedAt = OffsetDateTime.now();
    }
}
