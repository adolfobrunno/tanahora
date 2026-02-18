package com.abba.tanahora.application.messaging.handler;

import com.abba.tanahora.application.dto.AiMessageProcessorDto;
import com.abba.tanahora.application.dto.MessageReceivedType;
import com.abba.tanahora.application.exceptions.ReminderLimitException;
import com.abba.tanahora.application.messaging.AIMessage;
import com.abba.tanahora.application.messaging.classifier.MessageClassifier;
import com.abba.tanahora.application.messaging.flow.FlowState;
import com.abba.tanahora.application.notification.BasicWhatsAppMessage;
import com.abba.tanahora.domain.exceptions.InvalidRruleException;
import com.abba.tanahora.domain.model.Medication;
import com.abba.tanahora.domain.model.PatientRef;
import com.abba.tanahora.domain.model.Reminder;
import com.abba.tanahora.domain.model.User;
import com.abba.tanahora.domain.service.NotificationService;
import com.abba.tanahora.domain.service.PatientResolverService;
import com.abba.tanahora.domain.service.ReminderService;
import com.abba.tanahora.domain.service.UserService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(200)
public class MedicationRegistrationHandler implements HandleAndFlushMessageHandler {

    private final MessageClassifier messageClassifier;
    private final UserService userService;
    private final ReminderService reminderService;
    private final NotificationService notificationService;
    private final PatientResolverService patientResolverService;

    public MedicationRegistrationHandler(MessageClassifier messageClassifier,
                                         UserService userService,
                                         ReminderService reminderService,
                                         NotificationService notificationService,
                                         PatientResolverService patientResolverService) {
        this.messageClassifier = messageClassifier;
        this.userService = userService;
        this.reminderService = reminderService;
        this.notificationService = notificationService;
        this.patientResolverService = patientResolverService;
    }

    @Override
    public boolean supports(AIMessage message, FlowState state) {
        AiMessageProcessorDto dto = messageClassifier.classify(message, state);
        return dto != null && dto.getType() == MessageReceivedType.REMINDER_CREATION;
    }

    @Override
    public void handleAndFlush(AIMessage message, FlowState state) {
        AiMessageProcessorDto dto = messageClassifier.classify(message, state);
        if (dto == null || dto.getType() != MessageReceivedType.REMINDER_CREATION) {
            return;
        }
        if (dto.getMedication() == null || dto.getMedication().isBlank()) {
            return;
        }
        String normalizedRrule = normalizeRrule(dto.getRrule());
        if (normalizedRrule == null || normalizedRrule.isBlank()) {
            return;
        }

        User user = userService.register(message.getWhatsappId(), message.getContactName());
        PatientRef patient = patientResolverService.resolve(user, dto.getPatientName(), state.getLastPatientId(), true);
        if (patient == null) {
            notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .message("Qual paciente devo usar para esse lembrete?")
                    .build());
            return;
        }
        state.setLastPatientId(patient.getId());
        Medication medication = new Medication();
        medication.setName(dto.getMedication());
        medication.setDosage(dto.getDosage());
        try {
            Reminder reminder = reminderService.scheduleMedication(user, patient, medication, normalizedRrule);
            notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .message(reminder.createNewReminderMessage())
                    .build());

        } catch (ReminderLimitException e) {
            notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .message(
                            """
                                    Ops! 🙁 Você chegou ao limite de lembretes do plano gratuito.
                                    Para criar novos lembretes, faça o upgrade para o plano Premium.
                                    Se quiser, é só responder esta mensagem e eu te envio o link para upgrade.
                                    
                                    Se preferir, você também pode apagar um lembrete existente e cadastrar um novo no lugar.
                                    """)
                    .build());
        } catch (InvalidRruleException e) {
            notificationService.sendNotification(user, BasicWhatsAppMessage.builder()
                    .to(user.getWhatsappId())
                    .message("""
                            Ops! 🙁 Não consegui entender a recorrência informada.
                            Por favor, verifique a sintaxe e tente novamente.
                            
                            Exemplos:
                             - A cada 8 horas durante 7 dias
                             - Todo dia às 20:00
                             - Toda manhã até dia 10 de janeiro
                            """)
                    .build());
        }
        this.flush(state);

    }

    private String normalizeRrule(String rrule) {
        if (rrule == null) {
            return null;
        }
        String trimmed = rrule.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.regionMatches(true, 0, "RRULE:", 0, "RRULE:".length())) {
            String withoutPrefix = trimmed.substring("RRULE:".length()).trim();
            return withoutPrefix.isEmpty() ? null : withoutPrefix;
        }
        return trimmed;
    }
}
