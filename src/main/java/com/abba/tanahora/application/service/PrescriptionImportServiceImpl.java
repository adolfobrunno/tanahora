package com.abba.tanahora.application.service;

import com.abba.tanahora.application.dto.PrescriptionExtractedReminderDto;
import com.abba.tanahora.application.dto.PrescriptionExtractionResultDto;
import com.abba.tanahora.application.notification.BasicWhatsAppMessage;
import com.abba.tanahora.application.notification.InteractiveWhatsAppMessage;
import com.abba.tanahora.domain.model.*;
import com.abba.tanahora.domain.repository.MessageReceivedRepository;
import com.abba.tanahora.domain.repository.PrescriptionImportRepository;
import com.abba.tanahora.domain.service.*;
import com.abba.tanahora.infrastructure.whatsapp.WhatsAppMediaClient;
import com.whatsapp.api.domain.messages.Button;
import com.whatsapp.api.domain.messages.Reply;
import com.whatsapp.api.domain.messages.type.ButtonType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PrescriptionImportServiceImpl implements PrescriptionImportService {

    private static final String CONFIRM_PREFIX = "confirm_prescription:";
    private static final String CANCEL_PREFIX = "cancel_prescription:";
    private static final int MAX_PDF_PAGES = 5;

    private final MessageReceivedRepository messageReceivedRepository;
    private final PrescriptionImportRepository prescriptionImportRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final ReminderService reminderService;
    private final PatientResolverService patientResolverService;
    private final OpenAiApiService openAiApiService;
    private final WhatsAppMediaClient whatsAppMediaClient;

    @Override
    public void startImportFromMediaMessage(String messageId) {
        MessageReceived source = messageReceivedRepository.findById(messageId).orElse(null);
        if (source == null) {
            return;
        }

        User user = userService.register(source.getWhatsappId(), source.getContactName());
        if (!user.isPremium()) {
            notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .message("Leitura de receita por foto/PDF esta disponivel apenas no plano Premium. Responda esta mensagem para receber o link de upgrade.")
                    .build());
            return;
        }

        if (!supportsMediaType(source.getMessageType(), source.getMediaMimeType())) {
            notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .message("Formato de arquivo nao suportado. Envie uma imagem ou PDF da receita.")
                    .build());
            return;
        }

        PrescriptionImport prescriptionImport = new PrescriptionImport();
        prescriptionImport.setWhatsappId(user.getWhatsappId());
        prescriptionImport.setContactName(source.getContactName());
        prescriptionImport.setSourceMessageId(source.getId());
        prescriptionImport.setMediaType(source.getMessageType());
        prescriptionImport.setMediaId(source.getMediaId());
        prescriptionImport.setMediaMimeType(source.getMediaMimeType());
        prescriptionImport.setMediaFilename(source.getMediaFilename());
        prescriptionImport.setMediaSha256(source.getMediaSha256());
        prescriptionImport.setCaption(source.getMessage());
        prescriptionImport = prescriptionImportRepository.save(prescriptionImport);

        try {
            List<PrescriptionExtractedReminder> extracted = extractReminders(prescriptionImport);
            if (extracted.isEmpty()) {
                prescriptionImport.setStatus(PrescriptionImportStatus.FAILED);
                prescriptionImport.setErrorMessage("No reminder extracted");
                prescriptionImport.touch();
                prescriptionImportRepository.save(prescriptionImport);
                notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                        .to(user.getWhatsappId())
                        .message("Nao consegui identificar medicamentos na receita. Envie uma foto/PDF mais nitido para tentar novamente.")
                        .build());
                return;
            }

            prescriptionImport.setExtractedReminders(extracted);
            prescriptionImport.setStatus(PrescriptionImportStatus.AWAITING_CONFIRMATION);
            prescriptionImport.touch();

            String summary = buildConfirmationMessage(extracted);
            String interactiveMessageId = notificationService.sendNotification(user, InteractiveWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .text(summary)
                    .button(new Button().setType(ButtonType.REPLY).setReply(new Reply().setTitle("Confirmar").setId(CONFIRM_PREFIX + prescriptionImport.getId())))
                    .button(new Button().setType(ButtonType.REPLY).setReply(new Reply().setTitle("Cancelar").setId(CANCEL_PREFIX + prescriptionImport.getId())))
                    .build());

            prescriptionImport.setInteractiveMessageId(interactiveMessageId);
            prescriptionImportRepository.save(prescriptionImport);
        } catch (Exception e) {
            log.error("Failed to process prescription import id={}", prescriptionImport.getId(), e);
            prescriptionImport.setStatus(PrescriptionImportStatus.FAILED);
            prescriptionImport.setErrorMessage(e.getMessage());
            prescriptionImport.touch();
            prescriptionImportRepository.save(prescriptionImport);

            notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .message("Tive um problema para processar a receita. Tente novamente em instantes.")
                    .build());
        }
    }

    @Override
    public void confirmImport(String importId, String whatsappId, String contactName) {
        User user = userService.register(whatsappId, contactName);
        PrescriptionImport prescriptionImport = prescriptionImportRepository
                .findByIdAndWhatsappId(importId, whatsappId)
                .orElse(null);

        if (prescriptionImport == null || prescriptionImport.getStatus() != PrescriptionImportStatus.AWAITING_CONFIRMATION) {
            notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .message("Nao encontrei uma receita pendente para confirmar.")
                    .build());
            return;
        }

        List<Reminder> createdReminders = new ArrayList<>();
        try {
            for (PrescriptionExtractedReminder extractedReminder : prescriptionImport.getExtractedReminders()) {
                PatientRef patient = resolvePatient(user, extractedReminder.getPatientName());
                Medication medication = new Medication();
                medication.setName(defaultIfBlank(extractedReminder.getMedication(), "Medicamento"));
                medication.setDosage(defaultIfBlank(extractedReminder.getDosage(), "nao informado"));

                Reminder reminder = reminderService.scheduleMedication(user, patient, medication, extractedReminder.getRrule());
                createdReminders.add(reminder);
            }

            prescriptionImport.setStatus(PrescriptionImportStatus.COMPLETED);
            prescriptionImport.touch();
            prescriptionImportRepository.save(prescriptionImport);

            notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .message("Perfeito. Registrei " + createdReminders.size() + " lembrete(s) da receita.")
                    .build());
        } catch (Exception e) {
            log.error("Failed to confirm prescription import id={}", importId, e);
            createdReminders.forEach(reminderService::cancelReminder);

            notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .message("Nao consegui registrar todos os lembretes da receita. Nenhum lembrete foi mantido.")
                    .build());
        }
    }

    @Override
    public void cancelImport(String importId, String whatsappId, String contactName) {
        User user = userService.register(whatsappId, contactName);
        PrescriptionImport prescriptionImport = prescriptionImportRepository
                .findByIdAndWhatsappId(importId, whatsappId)
                .orElse(null);

        if (prescriptionImport == null) {
            notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .message("Nao encontrei uma receita pendente para cancelar.")
                    .build());
            return;
        }

        prescriptionImportRepository.delete(prescriptionImport);
        notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                .to(user.getWhatsappId())
                .message("Tudo certo. Registro da receita removido e nenhum lembrete foi criado.")
                .build());
    }

    private List<PrescriptionExtractedReminder> extractReminders(PrescriptionImport prescriptionImport) {
        WhatsAppMediaClient.MediaPayload mediaPayload = whatsAppMediaClient.downloadByMediaId(prescriptionImport.getMediaId());
        String mimeType = defaultIfBlank(mediaPayload.mimeType(), prescriptionImport.getMediaMimeType());

        if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).contains("pdf")) {
            return extractFromPdf(mediaPayload.bytes(), prescriptionImport.getCaption());
        }

        PrescriptionExtractionResultDto extraction = openAiApiService.sendPromptWithMedia(
                buildVisionPrompt(prescriptionImport.getCaption()),
                mediaPayload.bytes(),
                mimeType,
                PrescriptionExtractionResultDto.class
        );

        return normalizeExtraction(extraction);
    }

    private List<PrescriptionExtractedReminder> extractFromPdf(byte[] pdfBytes, String caption) {
        List<PrescriptionExtractedReminder> collected = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            String text = new PDFTextStripper().getText(document);
            if (text != null && !text.isBlank()) {
                PrescriptionExtractionResultDto extraction = openAiApiService.sendPrompt(
                        buildPdfTextPrompt(text, caption),
                        PrescriptionExtractionResultDto.class
                );
                collected.addAll(normalizeExtraction(extraction));
            }

            if (!collected.isEmpty()) {
                return deduplicate(collected);
            }

            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), MAX_PDF_PAGES);
            for (int i = 0; i < pages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 180);
                byte[] pageBytes = toPng(image);
                PrescriptionExtractionResultDto extraction = openAiApiService.sendPromptWithMedia(
                        buildVisionPrompt(caption + "\nPagina " + (i + 1)),
                        pageBytes,
                        "image/png",
                        PrescriptionExtractionResultDto.class
                );
                collected.addAll(normalizeExtraction(extraction));
            }
            return deduplicate(collected);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to extract reminders from PDF", e);
        }
    }

    private String buildVisionPrompt(String caption) {
        String safeCaption = caption == null ? "" : caption;
        return String.format("""
                Voce esta lendo uma receita medica em portugues.
                Extraia todos os medicamentos com dose e frequencia.
                Se houver duracao, use no RRULE com UNTIL.
                Se o nome do paciente existir na receita, informe em patientName.
                Se nao existir, use 'nao informado'.
                Para dosage, se nao identificar, use 'nao informado'.
                Retorne apenas JSON valido no schema solicitado.
                A RRULE deve ser iCalendar valida.
                Regras obrigatorias para RRULE valida:
                - Retorne somente o conteudo da regra, sem prefixo "RRULE:"
                - BYHOUR aceita apenas valores de 0 a 23 (nunca use 24)
                - BYMINUTE aceita apenas 0 a 59
                - BYSECOND aceita apenas 0 a 59
                - Se usar FREQ=HOURLY;INTERVAL=N, nao use BYHOUR, BYMINUTE ou BYSECOND na mesma regra
                - Se for "a cada 8 horas", use FREQ=HOURLY;INTERVAL=8 (sem BYHOUR/BYMINUTE/BYSECOND)
                - Se for "a cada 12 horas", use FREQ=HOURLY;INTERVAL=12 (sem BYHOUR/BYMINUTE/BYSECOND)
                - Se quiser horarios fixos no dia (ex: 00:00, 08:00, 16:00), use FREQ=DAILY com BYHOUR/BYMINUTE/BYSECOND e nao use FREQ=HOURLY
                - Se usar UNTIL, use formato UTC basico: yyyyMMdd'T'HHmmss'Z'
                Exemplos seguros:
                - FREQ=HOURLY;INTERVAL=8;UNTIL=20260221T235959Z
                - FREQ=DAILY;BYHOUR=0,8,16;BYMINUTE=0;BYSECOND=0;UNTIL=20260221T235959Z
                Hoje e %s.
                Contexto enviado pelo usuario: %s
                """, OffsetDateTime.now(), safeCaption);
    }

    private String buildPdfTextPrompt(String extractedText, String caption) {
        String safeCaption = caption == null ? "" : caption;
        return String.format("""
                Voce vai receber o texto de uma receita medica.
                Gere uma lista de lembretes de medicamentos.
                Retorne apenas JSON no schema solicitado.
                Regras:
                - Campo reminders deve conter todos os medicamentos identificados.
                - patientName: use 'nao informado' quando nao houver.
                - dosage: use 'nao informado' quando nao houver.
                - RRULE deve ser valida no formato iCalendar.
                - Nao usar prefixo "RRULE:".
                - BYHOUR deve conter apenas 0..23 (nunca 24).
                - BYMINUTE e BYSECOND devem estar entre 0..59.
                - Se usar FREQ=HOURLY;INTERVAL=N, nao usar BYHOUR/BYMINUTE/BYSECOND na mesma regra.
                - Se for "a cada 8 horas", use FREQ=HOURLY;INTERVAL=8 (sem BYHOUR/BYMINUTE/BYSECOND).
                - Se for "a cada 12 horas", use FREQ=HOURLY;INTERVAL=12 (sem BYHOUR/BYMINUTE/BYSECOND).
                - Se quiser horarios fixos no dia, use FREQ=DAILY com BYHOUR/BYMINUTE/BYSECOND e nao FREQ=HOURLY.
                - UNTIL deve estar em UTC no formato yyyyMMdd'T'HHmmss'Z'.
                - Exemplos seguros: FREQ=HOURLY;INTERVAL=8;UNTIL=20260221T235959Z e FREQ=DAILY;BYHOUR=0,8,16;BYMINUTE=0;BYSECOND=0;UNTIL=20260221T235959Z.
                Hoje e %s.
                Contexto enviado pelo usuario: %s
                
                Texto da receita:
                %s
                """, OffsetDateTime.now(), safeCaption, extractedText);
    }

    private List<PrescriptionExtractedReminder> normalizeExtraction(PrescriptionExtractionResultDto extraction) {
        if (extraction == null || extraction.getReminders() == null) {
            return List.of();
        }

        return extraction.getReminders().stream()
                .filter(Objects::nonNull)
                .map(this::toDomain)
                .filter(this::isValidReminder)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private PrescriptionExtractedReminder toDomain(PrescriptionExtractedReminderDto dto) {
        PrescriptionExtractedReminder reminder = new PrescriptionExtractedReminder();
        reminder.setMedication(trimToNull(dto.getMedication()));
        reminder.setDosage(defaultIfBlank(trimToNull(dto.getDosage()), "nao informado"));
        reminder.setRrule(normalizeRrule(dto.getRrule()));
        reminder.setPatientName(defaultIfBlank(trimToNull(dto.getPatientName()), "nao informado"));
        return reminder;
    }

    private boolean isValidReminder(PrescriptionExtractedReminder reminder) {
        return reminder.getMedication() != null && reminder.getRrule() != null;
    }

    private List<PrescriptionExtractedReminder> deduplicate(List<PrescriptionExtractedReminder> reminders) {
        Map<String, PrescriptionExtractedReminder> unique = new LinkedHashMap<>();
        for (PrescriptionExtractedReminder reminder : reminders) {
            String key = (defaultIfBlank(reminder.getMedication(), "") + "|" +
                    defaultIfBlank(reminder.getDosage(), "") + "|" +
                    defaultIfBlank(reminder.getRrule(), "") + "|" +
                    defaultIfBlank(reminder.getPatientName(), "")).toLowerCase(Locale.ROOT);
            unique.putIfAbsent(key, reminder);
        }
        return new ArrayList<>(unique.values());
    }

    private String buildConfirmationMessage(List<PrescriptionExtractedReminder> reminders) {
        StringBuilder sb = new StringBuilder();
        sb.append("Encontrei ").append(reminders.size()).append(" medicamento(s) na receita:\n\n");
        for (int i = 0; i < reminders.size(); i++) {
            PrescriptionExtractedReminder reminder = reminders.get(i);
            sb.append(i + 1)
                    .append(") ")
                    .append(defaultIfBlank(reminder.getMedication(), "Medicamento"))
                    .append(" - dose: ")
                    .append(defaultIfBlank(reminder.getDosage(), "nao informado"))
                    .append(" - RRULE: ")
                    .append(defaultIfBlank(reminder.getRrule(), "nao informado"));

            if (!isNotInformed(reminder.getPatientName())) {
                sb.append(" - paciente: ").append(reminder.getPatientName());
            }
            sb.append("\n");
        }
        sb.append("\nDeseja criar esses lembretes?");
        return sb.toString();
    }

    private PatientRef resolvePatient(User user, String patientName) {
        String normalizedName = isNotInformed(patientName) ? "Paciente" : patientName;
        PatientRef patient = patientResolverService.resolve(user, normalizedName, null, true);
        if (patient == null) {
            patient = patientResolverService.resolve(user, "Paciente", null, true);
        }
        if (patient == null) {
            throw new IllegalStateException("Unable to resolve patient");
        }
        return patient;
    }

    private boolean supportsMediaType(String messageType, String mimeType) {
        if (!"image".equalsIgnoreCase(defaultIfBlank(messageType, "")) &&
                !"document".equalsIgnoreCase(defaultIfBlank(messageType, ""))) {
            return false;
        }
        if (mimeType == null || mimeType.isBlank()) {
            return "image".equalsIgnoreCase(messageType);
        }
        String normalized = mimeType.toLowerCase(Locale.ROOT);
        return normalized.startsWith("image/") || normalized.equals("application/pdf");
    }

    private byte[] toPng(BufferedImage image) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        }
    }

    private boolean isNotInformed(String value) {
        if (value == null) {
            return true;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() || normalized.equals("nao informado") || normalized.equals("n�o informado");
    }

    private String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeRrule(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.regionMatches(true, 0, "RRULE:", 0, "RRULE:".length())) {
            return trimToNull(trimmed.substring("RRULE:".length()));
        }
        return trimmed;
    }
}
