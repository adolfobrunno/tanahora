package com.abba.tanahora.domain.service;

public interface PrescriptionImportService {

    void startImportFromMediaMessage(String messageId);

    void confirmImport(String importId, String whatsappId, String contactName);

    void cancelImport(String importId, String whatsappId, String contactName);
}
